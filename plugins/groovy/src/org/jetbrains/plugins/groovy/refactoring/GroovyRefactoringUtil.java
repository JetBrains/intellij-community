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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpr;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

/**
 * @author ilyas
 */
public abstract class GroovyRefactoringUtil {

  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place.getParent();
    while (parent != null &&
        !(parent instanceof GrCodeBlock) &&
        !(parent instanceof GrCaseBlock) &&
        !(parent instanceof GroovyFile) &&
        !isLoopOrForkStatement(parent)) {
      parent = parent.getParent();
    }
    return parent;
  }

  public static boolean isLoopOrForkStatement(PsiElement elem) {
    return elem instanceof GrForStatement ||
        elem instanceof GrWhileStatement ||
        elem instanceof GrIfStatement;
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
    if (isLoopOrForkStatement(scope)) {
      PsiElement son = expr;
      while (son.getParent() != null && !isLoopOrForkStatement(son.getParent())) {
        son = son.getParent();
      }
      assert scope.equals(son.getParent());
      accumulateOccurences(expr, son, occurences);
    } else {
      accumulateOccurences(expr, scope, occurences);
    }
    return occurences.toArray(PsiElement.EMPTY_ARRAY);
  }


  private static void accumulateOccurences(@NotNull PsiElement expr, @NotNull PsiElement scope, @NotNull ArrayList<PsiElement> acc) {
    if (scope.equals(expr)) {
     acc.add(expr);
      return;
    }
    for (PsiElement child : scope.getChildren()) {
      if (!(child instanceof GrTypeDefinition) &&
          !(child instanceof GrMethod && scope instanceof GroovyFile)) {
        Comparator<PsiElement> psiComparator = new Comparator<PsiElement>() {
          public int compare(PsiElement psiElement, PsiElement psiElement1) {
            if (psiElement instanceof GrParameter &&
                psiElement1 instanceof GrParameter &&
                PsiEquivalenceUtil.areElementsEquivalent(psiElement, psiElement1)) {
              return 0;
            } else {
              return 1;
            }
          }
        };
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, psiComparator, false)) {
          acc.add(child);
        } else {
          accumulateOccurences(expr, child, acc);
        }
      }
    }
  }

  /**
   * Indicates that the given expression is result expression of some all or method
   * and cannot be replace by variable definition
   *
   * @param expr
   * @return
   */
  public static boolean isResultExpression(@NotNull GrExpression expr) {
    if (expr.getType() != null && expr.getType().equals(PsiType.VOID)) {
      return false;
    }
    if (expr.getParent() instanceof GrClosableBlock ||
        expr.getParent() instanceof GrOpenBlock &&
            expr.getParent().getParent() != null &&
            expr.getParent().getParent() instanceof GrMethod) {
      GrCodeBlock parent = ((GrCodeBlock) expr.getParent());
      GrStatement[] statements = parent.getStatements();
      if (statements.length > 0 && expr.equals(statements[statements.length - 1])) {
        return true;
      }
    }
    return false;
  }


  // todo add type hierarchy
  public static HashMap<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type) {
    HashMap<String, PsiType> map = new HashMap<String, PsiType>();
    if (!PsiTypesUtil.unboxIfPossible(type.getCanonicalText()).contains(".")){
    map.put(PsiTypesUtil.unboxIfPossible(type.getCanonicalText()), type);
    } else {
      map.put(type.getPresentableText(), type);
    }
    return map;
  }

  public static GrExpression getUnparenthesizedExpr(GrExpression expr) {
    GrExpression operand = expr;
    while (operand instanceof GrParenthesizedExpr) {
      operand = ((GrParenthesizedExpr) operand).getOperand();
    }
    return operand;
  }

  public static boolean isAppropriateContainerForIntroduceVariable(PsiElement tempContainer) {
    return tempContainer instanceof GrOpenBlock ||
        tempContainer instanceof GrClosableBlock ||
        tempContainer instanceof GroovyFile ||
        tempContainer instanceof GrCaseBlock ||
        isLoopOrForkStatement(tempContainer);
  }
}
