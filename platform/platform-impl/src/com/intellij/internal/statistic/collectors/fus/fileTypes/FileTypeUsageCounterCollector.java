// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType;
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext;
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomWhiteListRule;
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger;
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

  public static void triggerEdit(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "edit");
  }

  public static void triggerSelect(@NotNull Project project, @Nullable VirtualFile file) {
    if (file != null) {
      trigger(project, file, "select");
    }
    else {
      final FeatureUsageData data = new FeatureUsageData().addAnonymizedPath(null);
      FUCounterUsageLogger.getInstance().logEvent(project, "file.types.usage", "select", data);
    }
  }

  public static void triggerOpen(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "open");
  }

  public static void triggerClosed(@NotNull Project project, @NotNull VirtualFile file) {
    trigger(project, file, "close");
  }

  private static void trigger(@NotNull Project project,
                              @NotNull VirtualFile file,
                              @NotNull String event) {
    final FeatureUsageData data = FileTypeUsagesCollector.newFeatureUsageData(file.getFileType()).addAnonymizedPath(file.getPath());
    for (FileTypeUsageSchemaDescriptorEP<FileTypeUsageSchemaDescriptor> ext : EP.getExtensionList()) {
      FileTypeUsageSchemaDescriptor instance = ext.getInstance();
      if (ext.schema == null) {
        LOG.warn("Extension " + ext.implementationClass + " should define a 'schema' attribute");
        continue;
      }

      if (instance.describes(file)) {
        data.addData("schema", getPluginInfo(instance.getClass()).isSafeToReport() ? ext.schema : "third.party");
        break;
      }
    }

    FUCounterUsageLogger.getInstance().logEvent(project, "file.types.usage", event, data);
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
