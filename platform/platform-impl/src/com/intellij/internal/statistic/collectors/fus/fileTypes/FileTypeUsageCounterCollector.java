// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.PluginBundledTemplate;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorComposite;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.IncompleteDependenciesService.DependenciesState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.intellij.internal.statistic.eventLog.events.EventFields.StringValidatedByCustomRule;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfoByDescriptor;

@ApiStatus.Internal
public final class FileTypeUsageCounterCollector extends CounterUsagesCollector {

  private static final EventLogGroup GROUP = new EventLogGroup("file.types.usage", 74);

  private static final ClassEventField FILE_EDITOR = EventFields.Class("file_editor");
  private static final EventField<String> SCHEMA = StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  private static final EventField<Boolean> IS_WRITABLE = EventFields.Boolean("is_writable");
  private static final EventField<Boolean> IS_PREVIEW_TAB = EventFields.Boolean("is_preview_tab");
  private static final EnumEventField<DependenciesState> INCOMPLETE_DEPENDENCIES_MODE =
    EventFields.Enum("incomplete_dependencies_mode", DependenciesState.class);

  static final String FILE_NAME_PATTERN = "file_name_pattern";
  static final String FILE_TEMPLATE_NAME = "file_template_name";

  private static final EventField<String> FILE_NAME_PATTERN_FIELD =
    StringValidatedByCustomRule(FILE_NAME_PATTERN, FileNamePatternCustomValidationRule.class);
  private static final EventField<String> FILE_TEMPLATE_FIELD =
    StringValidatedByCustomRule(FILE_TEMPLATE_NAME, BundledFileTemplateValidationRule.class);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static VarargEventId registerFileTypeEvent(String eventId, EventField<?>... extraFields) {
    EventField<?>[] baseFields = {EventFields.PluginInfoFromInstance, EventFields.FileType, EventFields.AnonymizedPath, SCHEMA};
    return GROUP.registerVarargEvent(eventId, ArrayUtil.mergeArrays(baseFields, extraFields));
  }

  private static final VarargEventId SELECT = registerFileTypeEvent("select");
  private static final VarargEventId EDIT =
    registerFileTypeEvent("edit", FILE_NAME_PATTERN_FIELD, EventFields.Dumb, INCOMPLETE_DEPENDENCIES_MODE);
  private static final VarargEventId OPEN = registerFileTypeEvent(
    "open", FILE_EDITOR, EventFields.TimeToShowMs, EventFields.DurationMs, IS_WRITABLE, IS_PREVIEW_TAB, FILE_NAME_PATTERN_FIELD,
    EventFields.Dumb,
    INCOMPLETE_DEPENDENCIES_MODE
  );
  private static final VarargEventId CLOSE = registerFileTypeEvent("close", IS_WRITABLE);

  private static final VarargEventId CREATE_BY_NEW_FILE = registerFileTypeEvent("create_by_new_file");
  private static final VarargEventId CREATE_WITH_FILE_TEMPLATE = registerFileTypeEvent("create_with_template",
                                                                                       FILE_TEMPLATE_FIELD, EventFields.PluginInfo);

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    logEdited(project, file);
  }

  public static void triggerSelect(@NotNull Project project, @Nullable VirtualFile file) {
    if (file != null) {
      log(SELECT, project, file, false);
    }
    else {
      logEmptyFile();
    }
  }

  public static void logCreated(@NotNull Project project, @NotNull VirtualFile file) {
    log(CREATE_BY_NEW_FILE, project, file, false);
  }

  public static void logCreated(@NotNull Project project, @NotNull VirtualFile file, @NotNull FileTemplate fileTemplate) {
    CREATE_WITH_FILE_TEMPLATE.log(project, pairs -> {
      pairs.addAll(buildCommonEventPairs(project, file, false));
      pairs.add(FILE_TEMPLATE_FIELD.with(fileTemplate.getName()));

      if (fileTemplate instanceof PluginBundledTemplate) {
        PluginDescriptor pluginDescriptor = ((PluginBundledTemplate)fileTemplate).getPluginDescriptor();
        pairs.add(EventFields.PluginInfo.with(getPluginInfoByDescriptor(pluginDescriptor)));
      }
    });
  }

  private static void logEdited(@NotNull Project project,
                                @NotNull VirtualFile file) {
    List<EventPair<?>> readActionPairs = computeDataInReadAction(project);
    EDIT.log(project, pairs -> {
      pairs.addAll(buildCommonEventPairs(project, file, false));
      addFileNamePattern(pairs, file);
      pairs.addAll(readActionPairs);
    });
  }

  public static void logOpened(@NotNull Project project,
                               @NotNull VirtualFile file,
                               @Nullable FileEditor fileEditor,
                               long timeToShow,
                               long durationMs,
                               @NotNull FileEditorComposite composite) {
    List<EventPair<?>> readActionPairs = computeDataInReadAction(project);

    OPEN.log(project, pairs -> {
      pairs.addAll(buildCommonEventPairs(project, file, true));
      if (fileEditor != null) {
        pairs.add(FILE_EDITOR.with(fileEditor.getClass()));
        pairs.add(IS_PREVIEW_TAB.with(composite.isPreview()));
      }
      pairs.add(EventFields.TimeToShowMs.with(timeToShow));
      if (durationMs != -1) {
        pairs.add(EventFields.DurationMs.with(durationMs));
      }
      addFileNamePattern(pairs, file);
      pairs.addAll(readActionPairs);
    });
  }

  private static @NotNull List<EventPair<?>> computeDataInReadAction(@NotNull Project project) {
    return ReadAction.compute(() -> {
      boolean isDumb = DumbService.isDumb(project);
      IncompleteDependenciesService service = project.getService(IncompleteDependenciesService.class);
      DependenciesState incompleteDependenciesMode = service.getState();
      return Arrays.asList(EventFields.Dumb.with(isDumb), INCOMPLETE_DEPENDENCIES_MODE.with(incompleteDependenciesMode));
    });
  }

  public static void triggerClosed(@NotNull Project project, @NotNull VirtualFile file) {
    log(CLOSE, project, file, true);
  }

  private static void log(@NotNull VarargEventId eventId, @NotNull Project project, @NotNull VirtualFile file, boolean withWritable) {
    eventId.log(project, pairs -> {
      pairs.addAll(buildCommonEventPairs(project, file, withWritable));
    });
  }

  private static List<@NotNull EventPair<?>> buildCommonEventPairs(@NotNull Project project,
                                                                   @NotNull VirtualFile file,
                                                                   boolean withWritable) {
    FileType fileType = file.getFileType();
    List<EventPair<?>> data = List.of(
      EventFields.PluginInfoFromInstance.with(fileType),
      EventFields.FileType.with(fileType),
      EventFields.AnonymizedPath.with(file.getPath()),
      SCHEMA.with(findSchema(project, file))
    );

    if (withWritable) {
      data = ContainerUtil.append(data, IS_WRITABLE.with(file.isWritable()));
    }
    return data;
  }

  private static void addFileNamePattern(@NotNull List<? super EventPair<?>> data, @NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (!(fileTypeManager instanceof FileTypeManagerImpl)) {
      return;
    }
    List<FileNameMatcher> fileNameMatchers = ((FileTypeManagerImpl)fileTypeManager).getStandardMatchers(fileType);
    Optional<FileNameMatcher> fileNameMatcher = fileNameMatchers.stream().filter(x -> x.acceptsCharSequence(file.getName())).findFirst();
    fileNameMatcher.ifPresent(matcher -> data.add(FILE_NAME_PATTERN_FIELD.with(matcher.getPresentableString())));
  }

  private static void logEmptyFile() {
    SELECT.log(EventFields.AnonymizedPath.with(null));
  }

  public static @Nullable String findSchema(@NotNull Project project,
                                            @NotNull VirtualFile file) {
    for (var ext : FileTypeSchemaValidator.EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();
      if (ext.schema == null) {
        Logger.getInstance(FileTypeUsageCounterCollector.class)
          .warn("Extension " + ext.implementationClass + " should define a 'schema' attribute");
        continue;
      }

      if (instance.describes(project, file)) {
        return PluginInfoDetectorKt.getPluginInfo(instance.getClass()).isSafeToReport() ? ext.schema : "third.party";
      }
    }
    return null;
  }
}
