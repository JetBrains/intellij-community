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
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.grails.lang.gsp.psi.groovy.api.GrGspDeclarationHolder;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.*;

/**
 * @author ilyas
 */
public abstract class GroovyRefactoringUtil {

  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place.getParent();
    while (parent != null &&
        !(parent instanceof GrCodeBlock) &&
        !(parent instanceof GrCaseSection) &&
        !(parent instanceof GroovyFileBase) &&
        !(parent instanceof GrGspDeclarationHolder) &&
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

  public static <T extends PsiElement> T findElementInRange(final GroovyFileBase file,
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
    if (element == null || element.getTextRange().getStartOffset() != startOffset) {
      return null;
    }
    return element;
  }

  public static PsiElement[] getExpressionOccurrences(@NotNull PsiElement expr, @NotNull PsiElement scope) {
    ArrayList<PsiElement> occurrences = new ArrayList<PsiElement>();
    Comparator<PsiElement> comparator = new Comparator<PsiElement>() {
      public int compare(PsiElement element1, PsiElement element2) {
        if (element1.equals(element2)) return 0;

        if (element1 instanceof GrParameter &&
            element2 instanceof GrParameter) {
          final String name1 = ((GrParameter) element1).getName();
          final String name2 = ((GrParameter) element2).getName();
          if (name1 != null && name2 != null) {
            return name1.compareTo(name2);
          }
        }
        return 1;
      }
    };

    if (isLoopOrForkStatement(scope)) {
      PsiElement son = expr;
      while (son.getParent() != null && !isLoopOrForkStatement(son.getParent())) {
        son = son.getParent();
      }
      assert scope.equals(son.getParent());
      collectOccurrences(expr, son, occurrences, comparator);
    } else {
      collectOccurrences(expr, scope, occurrences, comparator);
    }
    return occurrences.toArray(PsiElement.EMPTY_ARRAY);
  }


  private static void collectOccurrences(@NotNull PsiElement expr, @NotNull PsiElement scope, @NotNull ArrayList<PsiElement> acc, Comparator<PsiElement> comparator) {
    if (scope.equals(expr)) {
      acc.add(expr);
      return;
    }
    for (PsiElement child : scope.getChildren()) {
      if (!(child instanceof GrTypeDefinition) &&
          !(child instanceof GrMethod && scope instanceof GroovyFileBase)) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, comparator, false)) {
          acc.add(child);
        } else {
          collectOccurrences(expr, child, acc, comparator);
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
    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;
    map.put(type.getPresentableText(), type);

    return map;
  }

  public static GrExpression getUnparenthesizedExpr(GrExpression expr) {
    GrExpression operand = expr;
    while (operand instanceof GrParenthesizedExpression) {
      operand = ((GrParenthesizedExpression) operand).getOperand();
    }
    return operand;
  }

  public static boolean isAppropriateContainerForIntroduceVariable(PsiElement tempContainer) {
    return tempContainer instanceof GrOpenBlock ||
        tempContainer instanceof GrClosableBlock ||
        tempContainer instanceof GroovyFileBase ||
        tempContainer instanceof GrCaseSection ||
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
      sortOccurrences(occurences);
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

  public static void sortOccurrences(PsiElement[] occurences) {
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


  public static void highlightOccurrences(Project project, Editor editor, PsiElement[] elements) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    if (editor != null) {
      HighlightManager highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (elements.length > 0) {
        highlightManager.addOccurrenceHighlights(editor, elements, attributes, false, highlighters);
      }
    }
  }

  public static void trimSpacesAndComments(Editor editor, PsiFile file, boolean trimComments) {
    int start = editor.getSelectionModel().getSelectionStart();
    int end = editor.getSelectionModel().getSelectionEnd();
    while (file.findElementAt(start) instanceof PsiWhiteSpace ||
        (file.findElementAt(start) instanceof PsiComment && trimComments) ||
        (file.findElementAt(start) != null &&
            GroovyTokenTypes.mNLS.equals(file.findElementAt(start).getNode().getElementType()))) {
      start++;
    }
    while (file.findElementAt(end - 1) instanceof PsiWhiteSpace ||
        (file.findElementAt(end - 1) instanceof PsiComment && trimComments) ||
        (file.findElementAt(end - 1) != null &&
            (GroovyTokenTypes.mNLS.equals(file.findElementAt(end - 1).getNode().getElementType()) ||
                GroovyTokenTypes.mSEMI.equals(file.findElementAt(end - 1).getNode().getElementType())))) {
      end--;
    }

    editor.getSelectionModel().setSelection(start, end);
  }

  public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {
    if (!(file instanceof GroovyFileBase)) return PsiElement.EMPTY_ARRAY;
    Language language = GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);
    if (element1 instanceof PsiWhiteSpace) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof GrStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent instanceof GrCodeBlock) break;
      if (parent instanceof GroovyFileBase) break;
      if (parent instanceof GrCaseSection) break;
      if (parent == null) return PsiElement.EMPTY_ARRAY;
      parent = parent.getParent();
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset()) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset()) return PsiElement.EMPTY_ARRAY;

    if (parent instanceof GrCodeBlock && parent.getParent() instanceof GrBlockStatement &&
        element1 == ((GrCodeBlock) parent).getLBrace() && element2 == ((GrCodeBlock) parent).getRBrace()) {
      return new PsiElement[]{parent.getParent()};
    }

    // calcualte children
    PsiElement[] children = PsiElement.EMPTY_ARRAY;
    PsiElement psiChild = parent.getFirstChild();
    if (psiChild != null) {
      List<PsiElement> result = new ArrayList<PsiElement>();
      while (psiChild != null) {
        result.add(psiChild);
        psiChild = psiChild.getNextSibling();
      }
      children = result.toArray(new PsiElement[result.size()]);
    }


    ArrayList<PsiElement> possibleStatements = new ArrayList<PsiElement>();
    boolean flag = false;
    for (PsiElement child : children) {
      if (child.equals(element1)) {
        flag = true;
      }
      if (flag) {
        possibleStatements.add(child);
      }
      if (child.equals(element2)) {
        break;
      }
    }

    for (PsiElement element : possibleStatements) {
      if (!(element instanceof GrStatement ||
          element instanceof PsiWhiteSpace ||
          element instanceof PsiComment ||
          TokenSets.SEPARATORS.contains(element.getNode().getElementType()))) {
        return PsiElement.EMPTY_ARRAY;
      }
    }

    return possibleStatements.toArray(new PsiElement[possibleStatements.size()]);
  }

  public static boolean isSuperOrThisCall(GrStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof GrMethodCallExpression)) return false;
    GrMethodCallExpression expr = (GrMethodCallExpression) statement;
    GrExpression invoked = expr.getInvokedExpression();
    if (testForSuper) {
      if ("super".equals(invoked.getText())) return true;
    }
    if (testForThis) {
      if ("this".equals(invoked.getText())) return true;
    }

    return false;
  }

}
