// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.*;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorComposite;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ApiStatus.Internal
public final class FileTypeUsageCounterCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(FileTypeUsageCounterCollector.class);

  private static final ExtensionPointName<FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor>> EP =
    new ExtensionPointName<>("com.intellij.fileTypeUsageSchemaDescriptor");

  private static final EventLogGroup GROUP = new EventLogGroup("file.types.usage", 69);

  private static final ClassEventField FILE_EDITOR = EventFields.Class("file_editor");
  private static final EventField<String> SCHEMA = EventFields.StringValidatedByCustomRule("schema", FileTypeSchemaValidator.class);
  private static final EventField<Boolean> IS_WRITABLE = EventFields.Boolean("is_writable");
  private static final EventField<Boolean> IS_PREVIEW_TAB = EventFields.Boolean("is_preview_tab");
  private static final String FILE_NAME_PATTERN = "file_name_pattern";
  private static final EventField<String> FILE_NAME_PATTERN_FIELD =
    EventFields.StringValidatedByCustomRule(FILE_NAME_PATTERN, FileNamePatternCustomValidationRule.class);

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static VarargEventId registerFileTypeEvent(String eventId, EventField<?>... extraFields) {
    EventField<?>[] baseFields = {EventFields.PluginInfoFromInstance, EventFields.FileType, EventFields.AnonymizedPath, SCHEMA};
    return GROUP.registerVarargEvent(eventId, ArrayUtil.mergeArrays(baseFields, extraFields));
  }

  private static final VarargEventId SELECT = registerFileTypeEvent("select");
  private static final VarargEventId CREATE_BY_NEW_FILE = registerFileTypeEvent("create_by_new_file");
  private static final VarargEventId EDIT = registerFileTypeEvent("edit", FILE_NAME_PATTERN_FIELD);
  private static final VarargEventId OPEN = registerFileTypeEvent(
    "open", FILE_EDITOR, EventFields.TimeToShowMs, EventFields.DurationMs, IS_WRITABLE, IS_PREVIEW_TAB, FILE_NAME_PATTERN_FIELD
  );
  private static final VarargEventId CLOSE = registerFileTypeEvent("close", IS_WRITABLE);

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

  public static void triggerCreate(@NotNull Project project, @NotNull VirtualFile file) {
    log(CREATE_BY_NEW_FILE, project, file, false);
  }

  private static void logEdited(@NotNull Project project,
                                @NotNull VirtualFile file) {
    EDIT.log(project, pairs -> {
      pairs.addAll(buildCommonEventPairs(project, file, false));
      addFileNamePattern(pairs, file);
    });
  }

  public static void logOpened(@NotNull Project project,
                                @NotNull VirtualFile file,
                                @Nullable FileEditor fileEditor,
                                long timeToShow,
                                long durationMs,
                                @NotNull FileEditorComposite composite) {
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
    for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();
      if (ext.schema == null) {
        LOG.warn("Extension " + ext.implementationClass + " should define a 'schema' attribute");
        continue;
      }

      if (instance.describes(project, file)) {
        return PluginInfoDetectorKt.getPluginInfo(instance.getClass()).isSafeToReport() ? ext.schema : "third.party";
      }
    }
    return null;
  }

  static final class FileTypeUsageSchemaDescriptorEP<T> extends BaseKeyedLazyInstance<T> implements KeyedLazyInstance<T> {
    // these must be public for scrambling compatibility
    @Attribute("schema")
    public String schema;

    @Attribute("implementationClass")
    public String implementationClass;

    @Override
    protected @Nullable String getImplementationClassName() {
      return implementationClass;
    }

    @Override
    public String getKey() {
      return schema;
    }
  }

  public static final class FileTypeSchemaValidator extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return "file_type_schema";
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
        if (StringUtil.equals(ext.schema, data)) {
          return PluginInfoDetectorKt.getPluginInfo(ext.getInstance().getClass()).isSafeToReport() ?
                 ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }

  static final class MyAnActionListener implements AnActionListener {
    private static final Key<Long> LAST_EDIT_USAGE = Key.create("LAST_EDIT_USAGE");

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
      if (action instanceof EditorAction && ((EditorAction)action).getHandlerOfType(EditorWriteActionHandler.class) != null) {
        onChange(event.getDataContext());
      }
    }

    private static void onChange(DataContext dataContext) {
      final Editor editor = CommonDataKeys.HOST_EDITOR.getData(dataContext);
      if (editor == null) return;
      Project project = editor.getProject();
      if (project == null) return;
      VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
      if (file != null) {
        Long lastEdit = editor.getUserData(LAST_EDIT_USAGE);
        if (lastEdit == null || System.currentTimeMillis() - lastEdit > 60 * 1000) {
          editor.putUserData(LAST_EDIT_USAGE, System.currentTimeMillis());
          triggerEdit(project, file);
        }
      }
    }

    @Override
    public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
      onChange(dataContext);
    }
  }

  static final class FileNamePatternCustomValidationRule extends CustomValidationRule {
    @Override
    public @NotNull String getRuleId() {
      return FILE_NAME_PATTERN;
    }

    @Override
    protected @NotNull ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      final Object fileTypeName = context.eventData.get("file_type");
      final FileType fileType = fileTypeName != null ? FileTypeManager.getInstance().findFileTypeByName(fileTypeName.toString()) : null;
      if (fileType == null || fileType == UnknownFileType.INSTANCE)
        return ValidationResultType.THIRD_PARTY;

      FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      if (!(fileTypeManager instanceof FileTypeManagerImpl)) {
        return ValidationResultType.THIRD_PARTY;
      }
      List<FileNameMatcher> fileNameMatchers = ((FileTypeManagerImpl)fileTypeManager).getStandardMatchers(fileType);
      Optional<FileNameMatcher> fileNameMatcher = fileNameMatchers.stream().filter(x -> x.getPresentableString().equals(data)).findFirst();
      if (fileNameMatcher.isEmpty())
        return ValidationResultType.THIRD_PARTY;

      return acceptWhenReportedByJetBrainsPlugin(context);
    }
  }
}
