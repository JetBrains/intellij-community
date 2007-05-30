/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.Language;
import com.intellij.util.ReflectionCache;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author ilyas
 */
public abstract class GroovyRefactoringUtil {

  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place;
    while (parent != null &&
        !(parent instanceof GrCodeBlock) &&
        !(parent instanceof GrCaseBlock) &&
        !(parent instanceof GroovyFile)) {
      parent = parent.getParent();
    }
    return parent;
  }

  public static <T extends PsiElement> T findElementInRange(final GroovyFile file,
                                                            int startOffset,
                                                            int endOffset,
                                                            final Class<T> klass) {
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, file.getLanguage());
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, file.getLanguage());
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.getViewProvider().findElementAt(startOffset, file.getLanguage());
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.getViewProvider().findElementAt(endOffset - 1, file.getLanguage());
    }
    if (element2 == null || element1 == null) return null;
    final PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    final T element = ReflectionCache.isAssignable(klass, commonParent.getClass())
        ? (T) commonParent : PsiTreeUtil.getParentOfType(commonParent, klass);
    if (element == null || element.getTextRange().getStartOffset() != startOffset || element.getTextRange().getEndOffset() != endOffset) {
      return null;
    }
    return element;
  }

  public static PsiElement[] getExpressionOccurences(@NotNull PsiElement expr, @NotNull PsiElement scope) {
    ArrayList<PsiElement> occurences = new ArrayList<PsiElement>();
    accumulateOccurences(expr, scope, occurences);
    return occurences.toArray(PsiElement.EMPTY_ARRAY);
  }

  private static void accumulateOccurences(@NotNull PsiElement expr, @NotNull PsiElement scope, @NotNull ArrayList<PsiElement> acc) {
    for (PsiElement child : scope.getChildren()) {
      if (PsiEquivalenceUtil.areElementsEquivalent(child, expr)) {
        acc.add(child);
      } else {
        accumulateOccurences(expr, child, acc);
      }
    }
  }

  // todo add type hierarchy
  public static HashMap<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type){
    HashMap<String,  PsiType> map = new HashMap<String, PsiType>();
    map.put(type.getPresentableText(), type);
    return map;
  }

}
