/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.lang.ant.dom.AntDomFileDescription;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.rename.PsiElementRenameHandler;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
public final class AntRenameHandler extends PsiElementRenameHandler {
  
  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement[] elements = getElements(dataContext);
    return elements != null && elements.length > 1;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    final PsiElement[] elements = getElements(dataContext);
    if (elements != null && elements.length > 0) {
      invoke(project, new PsiElement[]{elements[0]}, dataContext);
    }
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    super.invoke(project, elements, dataContext);
  }

  @Nullable 
  private static PsiElement[] getElements(DataContext dataContext) {
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataContext);
    if (!(psiFile instanceof XmlFile && AntDomFileDescription.isAntFile((XmlFile)psiFile))) {
      return null;
    }
    final Editor editor = LangDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }
    return getPsiElementsIn(editor, psiFile);
  }
  
  @Nullable
  private static PsiElement[] getPsiElementsIn(final Editor editor, final PsiFile psiFile) {
    try {
      final PsiReference reference = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (reference == null) {
        return null;
      }
      final Collection<PsiElement> candidates = TargetElementUtil.getInstance().getTargetCandidates(reference);
      return ContainerUtil.toArray(candidates, new PsiElement[candidates.size()]);
    }
    catch (IndexNotReadyException e) {
      return null;
    }
  }
  
}
