/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.commander;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.text.ElementPresentation;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PsiDiffContentFactory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.ex.PsiDiffContentFactory");

  private PsiDiffContentFactory() {
  }

  @Nullable
  private static DiffContent fromPsiElement(PsiElement psiElement) {
    if (psiElement instanceof PsiFile) {
      return DiffContent.fromFile(psiElement.getProject(), ((PsiFile)psiElement).getVirtualFile());
    } else if (psiElement instanceof PsiDirectory) {
      return DiffContent.fromFile(psiElement.getProject(), ((PsiDirectory)psiElement).getVirtualFile());
    }
    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile == null) {
      String text = psiElement.getText();
      return text != null ? new SimpleContent(text) : null;
    }
    DiffContent wholeFileContent = DiffContent.fromFile(psiElement.getProject(), containingFile.getVirtualFile());
    if (wholeFileContent == null || wholeFileContent.getDocument() == null) return null;
    Project project = psiElement.getProject();
    return new FragmentContent(wholeFileContent, psiElement.getTextRange(), project);
  }

  @Nullable
  public static SimpleDiffRequest comparePsiElements(PsiElement psiElement1, PsiElement psiElement2) {
    if (!psiElement1.isValid() || !psiElement2.isValid()) return null;
    Project project = psiElement1.getProject();
    LOG.assertTrue(project == psiElement2.getProject());
    DiffContent content1 = fromPsiElement(psiElement1);
    DiffContent content2 = fromPsiElement(psiElement2);
    if (content1 == null || content2 == null) return null;
    final ElementPresentation presentation1 = ElementPresentation.forElement(psiElement1);
    final ElementPresentation presentation2 = ElementPresentation.forElement(psiElement2);
    String title = DiffBundle.message("diff.element.qualified.name.vs.element.qualified.name.dialog.title",
                                      presentation1.getQualifiedName(), presentation2.getQualifiedName());
    SimpleDiffRequest diffRequest = new SimpleDiffRequest(project, title);
    diffRequest.setContents(content1, content2);
    diffRequest.setContentTitles(presentation1.getQualifiedName(), presentation2.getQualifiedName());
    return diffRequest;
  }
}
