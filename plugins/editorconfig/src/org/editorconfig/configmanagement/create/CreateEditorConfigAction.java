// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.create;

import com.intellij.application.options.CodeStyle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.actions.NewFileActionWithCategory;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.lang.Language;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
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
import org.editorconfig.EditorConfigNotifier;
import org.editorconfig.configmanagement.export.EditorConfigSettingsWriter;
import org.editorconfig.configmanagement.extended.EditorConfigPropertyKind;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public final class CreateEditorConfigAction extends AnAction implements DumbAware, NewFileActionWithCategory {
  private static final Logger LOG = Logger.getInstance(CreateEditorConfigAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      CreateEditorConfigDialog dialog = new CreateEditorConfigDialog(project);
      if (dialog.showAndGet()) {
        final IdeView view = getIdeView(e);
        if (view != null) {
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
              Messages
                .showErrorDialog(project,
                                 EditorConfigBundle.message("notification.message.another.editorconfig.file.already.exists.in.0", dirVFile.getPath()),
                                 EditorConfigBundle.message("dialog.title.new.editorconfig.file"));
            }
          }
        }
      }
    }
  }

  @Override
  public @NotNull String getCategory() {
    return "EditorConfig";
  }

  private static @Nullable PsiFile getPsiFile(@NotNull Project project, @NotNull VirtualFile file) {
    Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document != null) {
      return PsiDocumentManager.getInstance(project).getPsiFile(document);
    }
    return null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }


  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    if (PlatformUtils.isRider()) {
      presentation.setVisible(false);
      return;
    }
    IdeView view = getIdeView(e);
    if (view != null) {
      presentation.setVisible(e.getProject() != null &&
                              isAvailableFor(view.getDirectories()));
    }
    else {
      presentation.setEnabledAndVisible(false);
    }
    presentation.setIcon(AllIcons.Nodes.Editorconfig);
  }

  private static boolean isAvailableFor(PsiDirectory @NotNull [] dirs) {
    for (PsiDirectory dir : dirs) {
      if (dir.getVirtualFile().getFileSystem().isReadOnly()) {
        return false;
      }
    }
    return dirs.length > 0;
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static @Nullable IdeView getIdeView(@NotNull AnActionEvent e) {
    return e.getData(LangDataKeys.IDE_VIEW);
  }

  private static @NotNull File getOutputFile(@NotNull VirtualFile dir) {
    return new File(dir.getPath() + File.separator + ".editorconfig");
  }

  private @Nullable VirtualFile export(@NotNull VirtualFile outputDir,
                                       @NotNull File outputFile,
                                       @NotNull Project project,
                                       @NotNull CodeStyleSettings settings,
                                       boolean isRoot,
                                       boolean commentOutProperties,
                                       @NotNull List<Language> languages,
                                       EditorConfigPropertyKind @NotNull ... propertyKinds) {
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
      new Notification(EditorConfigNotifier.GROUP_DISPLAY_ID, EditorConfigBundle.message("notification.title.editorconfig.creation.failed"), e.getMessage(),
                       NotificationType.ERROR));
  }
}
