// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.*;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;
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
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.ArrayUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfo;

public class FileTypeUsageCounterCollector extends CounterUsagesCollector {
  private static final Logger LOG = Logger.getInstance(FileTypeUsageCounterCollector.class);

  private static final ExtensionPointName<FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor>> EP =
    ExtensionPointName.create("com.intellij.fileTypeUsageSchemaDescriptor");

  private static final EventLogGroup GROUP = new EventLogGroup("file.types.usage", FeatureUsageLogger.INSTANCE.getConfig().getVersion());

  private static final EventField<String> FILE_TYPE = EventFields.String("file_type").withCustomRule("file_type");
  private static final EventField<String> SCHEMA = EventFields.String("schema").withCustomRule("file_type_schema");
  private static final EventField<Boolean> IS_WRITABLE = EventFields.Boolean("is_writable");

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  private static VarargEventId registerFileTypeEvent(String eventId, EventField<?> ... extraFields) {
    EventField<?>[] baseFields = {EventFields.PluginInfoFromInstance, FILE_TYPE, EventFields.AnonymizedPath, SCHEMA};
    return GROUP.registerVarargEvent(eventId, ArrayUtil.mergeArrays(baseFields, extraFields));
  }

  private static final VarargEventId SELECT = registerFileTypeEvent("select");
  private static final VarargEventId EDIT = registerFileTypeEvent("edit");
  private static final VarargEventId OPEN = registerFileTypeEvent("open", IS_WRITABLE);
  private static final VarargEventId CLOSE = registerFileTypeEvent("close", IS_WRITABLE);

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    log(EDIT, project, file);
  }

  public static void triggerSelect(@NotNull Project project, @Nullable VirtualFile file) {
    if (file != null) {
      log(SELECT, project, file);
    }
    else {
      logEmptyFile();
    }
  }

  public static void triggerOpen(@NotNull Project project, @NotNull VirtualFile file) {
    OPEN.log(project, ArrayUtil.append(buildCommonEventPairs(file), IS_WRITABLE.with(file.isWritable())));
  }

  public static void triggerClosed(@NotNull Project project, @NotNull VirtualFile file) {
    CLOSE.log(project, ArrayUtil.append(buildCommonEventPairs(file), IS_WRITABLE.with(file.isWritable())));
  }

  private static void log(@NotNull VarargEventId eventId, @NotNull Project project, @NotNull VirtualFile file) {
    eventId.log(project, buildCommonEventPairs(file));
  }

  private static EventPair<?> @NotNull [] buildCommonEventPairs(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    return new EventPair[]{EventFields.PluginInfoFromInstance.with(fileType),
      FILE_TYPE.with(FileTypeUsagesCollector.getSafeFileTypeName(fileType)),
      EventFields.AnonymizedPath.with(file.getPath()),
      SCHEMA.with(findSchema(file))};
  }

  private static void logEmptyFile() {
    SELECT.log(EventFields.AnonymizedPath.with(null));
  }

  private static @Nullable String findSchema(@NotNull VirtualFile file) {
    for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();
      if (ext.schema == null) {
        LOG.warn("Extension " + ext.implementationClass + " should define a 'schema' attribute");
        continue;
      }

      if(instance.describes(file)) {
        return getPluginInfo(instance.getClass()).isSafeToReport() ? ext.schema : "third.party";
      }
    }
    return null;
  }

  public static final class FileTypeUsageSchemaDescriptorEP<T> extends BaseKeyedLazyInstance<T> implements KeyedLazyInstance<T> {
    // these must be public for scrambling compatibility
    @Attribute("schema")
    public String schema;

    @Attribute("implementationClass")
    public String implementationClass;

    @Nullable
    @Override
    protected String getImplementationClassName() {
      return implementationClass;
    }

    @Override
    public String getKey() {
      return schema;
    }
  }

  public static final class FileTypeSchemaValidator extends CustomValidationRule {

    @Override
    public boolean acceptRuleId(@Nullable String ruleId) {
      return "file_type_schema".equals(ruleId);
    }

    @NotNull
    @Override
    protected ValidationResultType doValidate(@NotNull String data, @NotNull EventContext context) {
      if (isThirdPartyValue(data)) return ValidationResultType.ACCEPTED;

      for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
        if (StringUtil.equals(ext.schema, data)) {
          return getPluginInfo(ext.getInstance().getClass()).isSafeToReport() ?
                 ValidationResultType.ACCEPTED : ValidationResultType.THIRD_PARTY;
        }
      }
      return ValidationResultType.REJECTED;
    }
  }

  public static class MyAnActionListener implements AnActionListener {
    private static final Key<Long> LAST_EDIT_USAGE = Key.create("LAST_EDIT_USAGE");

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
      if (action instanceof EditorAction && ((EditorAction)action).getHandlerOfType(EditorWriteActionHandler.class) != null) {
        onChange(dataContext);
      }
    }

    private static void onChange(DataContext dataContext) {
      final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
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

  public static class MyFileEditorManagerListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      triggerOpen(source.getProject(), file);
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
      triggerClosed(source.getProject(), file);
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
      triggerSelect(event.getManager().getProject(), event.getNewFile());
    }
  }
}
