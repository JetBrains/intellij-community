// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
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
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;

public class EditorConfigPreviewFile extends LightVirtualFile implements CodeStyleSettingsListener {
  private final Project  myProject;
  private final String   myOriginalPath;
  private final Document myDocument;

  EditorConfigPreviewFile(@NotNull Project project, @NotNull VirtualFile originalFile, @NotNull Document document) {
    super(originalFile.getName());
    myProject = project;
    myOriginalPath = originalFile.getPath();
    myDocument = document;
    final Language language = EditorConfigEditorProvider.getLanguage(originalFile);
    if (language != null) {
      super.setLanguage(language);
    }
    super.setContent(this, myDocument.getText(), false);
    reformat();
    CodeStyleSettingsManager.getInstance(project).addListener(this);
  }

  @NotNull
  private PsiFile createPsi(@NotNull FileType fileType) {
    return PsiFileFactory.getInstance(myProject)
      .createFileFromText(
        "preview", fileType, myDocument.getText(), LocalTimeCounter.currentTime(), false);
  }

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    reformat();
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
            CodeStyle.doWithTemporarySettings(
              myProject, settings, () -> CodeStyleManager.getInstance(myProject).reformatText(psiFile, 0, psiFile.getTextLength()));
            myDocument.replaceString(0, myDocument.getTextLength(), psiFile.getText());
          }
        }),
      EditorConfigBundle.message("command.name.reformat"), null);
  }

  @Nullable
  public PsiFile resolveOriginalPsi() {
    VirtualFile virtualFile =  VfsUtil.findFile(Paths.get(myOriginalPath), true);
    if (virtualFile != null) {
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document != null) {
        return PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      }
    }
    return null;
  }

  public void unregisterListener() {
    CodeStyleSettingsManager.removeListener(myProject, this);
  }
}
