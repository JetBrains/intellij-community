// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.create;

import com.intellij.application.options.CodeStyle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.lang.Language;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.PlatformUtils;
import org.editorconfig.configmanagement.export.EditorConfigSettingsWriter;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.editorconfig.plugincomponents.EditorConfigNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class CreateEditorConfigAction extends AnAction implements DumbAware {
  private final static Logger LOG = Logger.getInstance(CreateEditorConfigAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CreateEditorConfigDialog dialog = new CreateEditorConfigDialog();
    if (dialog.showAndGet()) {
      final IdeView view = getIdeView(e);
      Project project = e.getProject();
      if (view != null && project != null) {
        CodeStyleSettings settings = CodeStyle.getSettings(project);
        PsiDirectory dir = view.getOrChooseDirectory();
        if (dir != null) {
          final VirtualFile dirVFile = dir.getVirtualFile();
          File outputFile = getOutputFile(dirVFile);
          if (!outputFile.exists()) {
            VirtualFile target = ApplicationManager.getApplication().runWriteAction(
              (Computable<VirtualFile>)() -> export(dirVFile, outputFile, project, settings,
                                                    dialog.isRoot(),
                                                    dialog.isCommentProperties(),
                                                    dialog.getLanguages(),
                                                    dialog.getPropertyKinds()));
            if (target != null) {
              OpenFileAction.openFile(target, project);
              PsiFile psiFile = getPsiFile(project, target);
              if (psiFile != null) {
                view.selectElement(psiFile);
              }
            }
          }
          else {
            Messages.showErrorDialog(project, "Another EditorConfig file already exists in " + dirVFile.getPath(), "New EditorConfig File");
          }
        }
      }
    }
  }

  @Nullable
  private static PsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (PlatformUtils.isRider()) {
      presentation.setVisible(false);
      return;
    }
    presentation.setIcon(AllIcons.Nodes.Editorconfig);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  @Nullable
  protected IdeView getIdeView(@NotNull AnActionEvent e) {
    return e.getData(LangDataKeys.IDE_VIEW);
  }

  @NotNull
  private static File getOutputFile(@NotNull VirtualFile dir) {
    return new File(dir.getPath() + File.separator + ".editorconfig");
  }

  @Nullable
  private VirtualFile export(@NotNull VirtualFile outputDir,
                             @NotNull File outputFile,
                             @NotNull Project project,
                             @NotNull CodeStyleSettings settings,
                             boolean isRoot,
                             boolean commentOutProperties,
                             @NotNull List<Language> languages,
                             @NotNull EditorConfigPropertyKind... propertyKinds) {
    try {
      VirtualFile target = outputDir.createChildData(this, outputFile.getName());
      try (EditorConfigSettingsWriter settingsWriter =
             new EditorConfigSettingsWriter(project, target.getOutputStream(this), settings, isRoot, commentOutProperties)
               .forLanguages(languages)
               .forPropertyKinds(propertyKinds)) {
        settingsWriter.writeSettings();
        return target;
      }
    }
    catch (Exception e) {
      notifyFailed(e);
      return null;
    }
  }

  private static void notifyFailed(@NotNull Exception e) {
    LOG.warn(e);
    Notifications.Bus.notify(
      new Notification(EditorConfigNotifier.GROUP_DISPLAY_ID, "EditorConfig Creation Failed", e.getMessage(),
                       NotificationType.ERROR));
  }
}
