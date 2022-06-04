// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.*;
import com.intellij.lang.Language;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.editorconfig.Utils;
import org.editorconfig.configmanagement.EditorConfigEncodingCache.CharsetData;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

import static org.editorconfig.EditorConfigNotifier.GROUP_DISPLAY_ID;

public class EditorConfigEncodingInspection extends LocalInspectionTool {

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                  @NotNull InspectionManager manager,
                                                  boolean isOnTheFly) {
    if (!file.equals(getMainPsi(file))) {
      return null;
    }
    Project project = manager.getProject();
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && Utils.isEnabled(CodeStyle.getSettings(project)) && virtualFile.isWritable()) {
      if (isHardcodedCharsetOrFailed(virtualFile)) {
        return null;
      }
      EditorConfigEncodingCache encodingCache = EditorConfigEncodingCache.getInstance();
      if (encodingCache.isIgnored(virtualFile)) return null;
      CharsetData charsetData = encodingCache.getCharsetData(file.getProject(), file.getVirtualFile(), false);
      if (charsetData != null) {
        if (!virtualFile.getCharset().equals(charsetData.getCharset())) {
          return new ProblemDescriptor[]{
            manager.createProblemDescriptor(
              file,
              EditorConfigBundle.message("inspection.file.encoding.mismatch.descriptor", charsetData.getCharset().displayName()),
              new LocalQuickFix[]{
                new ApplyEditorConfigEncodingQuickFix(),
                new IgnoreFileQuickFix()
              },
              ProblemHighlightType.WARNING,
              isOnTheFly,
              false)
          };
        }
      }
    }
    return null;
  }

  private static boolean isHardcodedCharsetOrFailed(@NotNull VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    try {
      String charsetName = fileType.getCharset(virtualFile, virtualFile.contentsToByteArray());
      return charsetName != null;
    }
    catch (IOException e) {
      return true;
    }
  }

  private static @NotNull PsiFile getMainPsi(@NotNull PsiFile psiFile) {
    Language baseLanguage = psiFile.getViewProvider().getBaseLanguage();
    return psiFile.getViewProvider().getPsi(baseLanguage);
  }

  private static class ApplyEditorConfigEncodingQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return EditorConfigBundle.message("inspection.file.encoding.apply");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      VirtualFile file = descriptor.getPsiElement().getContainingFile().getVirtualFile();
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null) {
        EditorConfigEncodingCache.getInstance().computeAndCacheEncoding(project, file);
        try {
          LoadTextUtil.write(project, file, file, document.getText(), document.getModificationStamp());
        }
        catch (IOException e) {
          showError(project,
                    EditorConfigBundle.message("inspection.file.encoding.save.error"),
                    e.getLocalizedMessage());
        }
      }
    }
  }

  private static class IgnoreFileQuickFix implements LocalQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return EditorConfigBundle.message("inspection.file.encoding.ignore");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      VirtualFile file = descriptor.getPsiElement().getContainingFile().getVirtualFile();
      EditorConfigEncodingCache.getInstance().setIgnored(file);
    }
  }

  private static void showError(@NotNull Project project, @Nls @NotNull String title, @Nls @NotNull String message) {
    NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_DISPLAY_ID);
    Notifications.Bus.notify(group.createNotification(title, message, NotificationType.ERROR),
                             project);
  }
}
