// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.commander;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.contents.DocumentContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.ElementPresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class PsiDiffContentFactory {
  private static final Logger LOG = Logger.getInstance(PsiDiffContentFactory.class);

  private PsiDiffContentFactory() {
  }

  private static @Nullable DiffContent fromPsiElement(@NotNull PsiElement psiElement) {
    DiffContentFactory factory = DiffContentFactory.getInstance();
    if (psiElement instanceof PsiFile) {
      return factory.create(psiElement.getProject(), ((PsiFile)psiElement).getVirtualFile());
    }
    else if (psiElement instanceof PsiDirectory) {
      return factory.create(psiElement.getProject(), ((PsiDirectory)psiElement).getVirtualFile());
    }
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      String text = psiElement.getText();
      if (text == null) return null;
      return factory.create(psiElement.getProject(), text, psiElement.getLanguage().getAssociatedFileType(), false);
    }
    DocumentContent wholeFileContent = factory.createDocument(psiElement.getProject(), containingFile.getVirtualFile());
    if (wholeFileContent == null) return null;
    return factory.createFragment(psiElement.getProject(), wholeFileContent, psiElement.getTextRange());
  }

  public static @Nullable DiffRequest comparePsiElements(@NotNull PsiElement psiElement1, @NotNull PsiElement psiElement2) {
    if (!psiElement1.isValid() || !psiElement2.isValid()) return null;
    Project project = psiElement1.getProject();
    LOG.assertTrue(project == psiElement2.getProject());
    DiffContent content1 = fromPsiElement(psiElement1);
    DiffContent content2 = fromPsiElement(psiElement2);
    if (content1 == null || content2 == null) return null;
    final ElementPresentation presentation1 = ElementPresentation.forElement(psiElement1);
    final ElementPresentation presentation2 = ElementPresentation.forElement(psiElement2);
    String title = DiffBundle
      .message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
               presentation1.getQualifiedName(), presentation2.getQualifiedName());
    return new SimpleDiffRequest(title, content1, content2, presentation1.getQualifiedName(), presentation2.getQualifiedName());
  }
}
