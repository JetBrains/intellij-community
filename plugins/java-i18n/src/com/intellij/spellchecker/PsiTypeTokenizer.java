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
package com.intellij.spellchecker;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.spellchecker.inspections.SplitterFactory;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class PsiTypeTokenizer extends Tokenizer<PsiTypeElement> {

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiTypeElement element) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(element.getType());

    if (psiClass == null || psiClass.getContainingFile() == null || psiClass.getContainingFile().getVirtualFile() == null) {
      return null;
    }

    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();

    final boolean isInSource = (virtualFile != null) && fileIndex.isInContent(virtualFile);

    return !isInSource
           ? null
           : new Token[]{
             new Token<PsiTypeElement>(element, element.getText(), true, 0, getRangeToCheck(element.getText(), psiClass.getName()),
                                       SplitterFactory.getInstance().getIdentifierSplitter())};
  }

  @NotNull
  private TextRange getRangeToCheck(@NotNull String text, @NotNull String name) {
    final int i = text.indexOf(name);
    return new TextRange(i, i + name.length());
  }
}