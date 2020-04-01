// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.EventField;
import com.intellij.internal.statistic.eventLog.EventFields;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.VarargEventId;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
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
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfo;

public class FileTypeUsageCounterCollector {
  private static final Logger LOG = Logger.getInstance(FileTypeUsageCounterCollector.class);

  private static final ExtensionPointName<FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor>> EP =
    ExtensionPointName.create("com.intellij.fileTypeUsageSchemaDescriptor");

  private static final EventLogGroup GROUP = EventLogGroup.byId("file.types.usage");

  private static class FileTypeEventId extends VarargEventId {
    public static final EventField<String> FILE_TYPE = EventFields.String("file_type");
    public static final EventField<String> SCHEMA = EventFields.String("schema");

    private FileTypeEventId(@NotNull String eventId) {
      super(GROUP, eventId, EventFields.Project, EventFields.PluginInfo, FILE_TYPE, EventFields.AnonymizedPath, SCHEMA);
    }

    public void log(@NotNull Project project, @NotNull VirtualFile file) {
      FileType fileType = file.getFileType();
      log(EventFields.Project.with(project),
          EventFields.PluginInfo.with(getPluginInfo(fileType.getClass())),
          FILE_TYPE.with(FileTypeUsagesCollector.getSafeFileTypeName(fileType)),
          EventFields.AnonymizedPath.with(file.getPath()),
          SCHEMA.with(findSchema(file)));
    }

    public void logEmptyFile() {
      log(EventFields.AnonymizedPath.with(null));
    }
  }

  private static final FileTypeEventId SELECT = new FileTypeEventId("select");
  private static final FileTypeEventId EDIT = new FileTypeEventId("edit");
  private static final FileTypeEventId OPEN = new FileTypeEventId("open");
  private static final FileTypeEventId CLOSE = new FileTypeEventId("close");

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    EDIT.log(project, file);
  }

  public static void triggerSelect(@NotNull Project project, @Nullable VirtualFile file) {
    if (file != null) {
      SELECT.log(project, file);
    }
    else {
      SELECT.logEmptyFile();
    }
  }

  public static void triggerOpen(@NotNull Project project, @NotNull VirtualFile file) {
    OPEN.log(project, file);
  }

  public static void triggerClosed(@NotNull Project project, @NotNull VirtualFile file) {
    CLOSE.log(project, file);
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

  public static final class FileTypeSchemaValidator extends CustomWhiteListRule {

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
      if (action instanceof EditorAction && ((EditorAction)action).getHandler() instanceof EditorWriteActionHandler) {
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
