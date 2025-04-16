// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.editorconfig.common.EditorConfigBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.*;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;

final class EditorConfigPreviewFile extends LightVirtualFile implements CodeStyleSettingsListener {
  private final Project  myProject;
  private final String   myOriginalPath;
  private final Document myDocument;

  EditorConfigPreviewFile(@NotNull Project project,
                          @NotNull VirtualFile originalFile,
                          @NotNull Document document,
                          @NotNull Disposable disposable) {
    super(originalFile.getName());

    myProject = project;
    myOriginalPath = originalFile.getPath();
    myDocument = document;
    final Language language = EditorConfigEditorProvider.Companion.getLanguage(originalFile);
    if (language != null) {
      super.setLanguage(language);
    }
    super.setContent(this, myDocument.getText(), false);
    reformat();
    CodeStyleSettingsManager.getInstance(project).subscribe(this, disposable);
  }

  private @NotNull PsiFile createPsi(@NotNull FileType fileType) {
    return PsiFileFactory.getInstance(myProject)
      .createFileFromText("preview", fileType, myDocument.getText(), LocalTimeCounter.currentTime(), false);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    VirtualFile virtualFile = event.getVirtualFile();
    if (virtualFile == null || isOriginalFile(virtualFile)) {
      reformat();
    }
  }

  private boolean isOriginalFile(@NotNull VirtualFile file) {
    return file.getPath().equals(myOriginalPath);
  }

  private void reformat() {
    if (!myProject.isInitialized()) return;
    CommandProcessor.getInstance().executeCommand(
      myProject,
      () -> ApplicationManager.getApplication().runWriteAction(
        () -> {
          PsiFile originalPsiFile = resolveOriginalPsi();
          if (originalPsiFile != null) {
            CodeStyleSettings settings = CodeStyle.getSettings(originalPsiFile);
            PsiFile psiFile = createPsi(originalPsiFile.getFileType());
            psiFile.putUserData(PsiFileFactory.ORIGINAL_FILE, originalPsiFile);
            CodeStyle.runWithLocalSettings(
              myProject, settings, () -> CodeStyleManager.getInstance(myProject).reformatText(psiFile, 0, psiFile.getTextLength()));
            myDocument.replaceString(0, myDocument.getTextLength(), psiFile.getText());
          }
        }),
      EditorConfigBundle.message("command.name.reformat"), null);
  }

  public @Nullable PsiFile resolveOriginalPsi() {
    VirtualFile virtualFile =  VfsUtil.findFile(Paths.get(myOriginalPath), true);
    if (virtualFile != null) {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document != null) {
        return PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      }
    }
    return null;
  }
}
