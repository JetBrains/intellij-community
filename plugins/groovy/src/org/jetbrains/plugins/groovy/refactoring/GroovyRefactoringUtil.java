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
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
import java.util.Arrays;

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
    String unboxed = PsiTypesUtil.unboxIfPossible(type.getCanonicalText());
    if (unboxed != null && !unboxed.contains(".")) {
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

  /**
   * Calculates position to which new variable definition will be inserted.
   *
   * @param container
   * @param occurences
   * @param replaceAllOccurences
   * @param expr                 expression to be introduced as a variable
   * @return PsiElement, before what new definition will be inserted
   */
  @Nullable
  public static PsiElement calculatePositionToInsertBefore(@NotNull PsiElement container,
                                                           PsiElement expr,
                                                           PsiElement[] occurences,
                                                           boolean replaceAllOccurences) {
    if (occurences.length == 0) return null;
    PsiElement candidate;
    if (occurences.length == 1 || !replaceAllOccurences) {
      candidate = expr;
    } else {
      sortOccurences(occurences);
      candidate = occurences[0];
    }
    while (candidate != null && !container.equals(candidate.getParent())) {
      candidate = candidate.getParent();
    }
    if (candidate == null) {
      return null;
    }
    if ((container instanceof GrWhileStatement) &&
        candidate.equals(((GrWhileStatement) container).getCondition())) {
      return container;
    }
    if ((container instanceof GrIfStatement) &&
        candidate.equals(((GrIfStatement) container).getCondition())) {
      return container;
    }
    if ((container instanceof GrForStatement) &&
        candidate.equals(((GrForStatement) container).getClause())) {
      return container;
    }
    return candidate;
  }

  public static void sortOccurences(PsiElement[] occurences) {
    Arrays.sort(occurences, new Comparator<PsiElement>() {
      public int compare(PsiElement elem1, PsiElement elem2) {
        final int offset1 = elem1.getTextRange().getStartOffset();
        final int offset2 = elem2.getTextRange().getStartOffset();
        return offset1 - offset2;
      }
    });
  }

  public static boolean isLocalVariable(GrVariable variable) {
    return !(variable instanceof GrField ||
        variable instanceof GrParameter);
  }


  public static void highlightOccurences(Project project, Editor editor, PsiElement[] elements) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (elements.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, elements, attributes, true, highlighters);
      }
    }
  }
}
