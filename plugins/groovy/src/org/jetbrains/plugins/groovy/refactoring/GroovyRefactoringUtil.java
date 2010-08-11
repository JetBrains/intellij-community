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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.refactoring.util.RefactoringUtil.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.*;

import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrBreakStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrContinueStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.branch.GrReturnStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.*;

/**
 * @author ilyas
 */
public abstract class GroovyRefactoringUtil {

  public static final Collection<String> KEYWORDS = ContainerUtil.map(
      GroovyTokenTypes.KEYWORDS.getTypes(),
      new Function<IElementType, String>() {
        public String fun(IElementType type) {
          return type.toString();
        }
      });

  private static final String[] finalModifiers = new String[]{PsiModifier.FINAL};

  @Nullable
  public static PsiElement getEnclosingContainer(PsiElement place) {
    PsiElement parent = place.getParent();
    while (true) {
      if (parent == null) {
        return null;
      }
      if (parent instanceof GrDeclarationHolder && !(parent instanceof GrClosableBlock && parent.getParent() instanceof GrStringInjection)) {
        return parent;
      }
      if (parent instanceof GrLoopStatement) {
        return parent;
      }

      parent = parent.getParent();
    }
  }

  @Nullable
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
    assert commonParent != null;
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

    if (scope instanceof GrLoopStatement) {
      PsiElement son = expr;
      while (son.getParent() != null && !(son.getParent() instanceof GrLoopStatement)) {
        son = son.getParent();
      }
      assert scope.equals(son.getParent());
      collectOccurrences(expr, son, occurrences, comparator);
    } else {
      collectOccurrences(expr, scope, occurrences, comparator);
    }
    return occurrences.toArray(new PsiElement[occurrences.size()]);
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


  // todo add type hierarchy
  public static HashMap<String, PsiType> getCompatibleTypeNames(@NotNull PsiType type) {
    HashMap<String, PsiType> map = new HashMap<String, PsiType>();
    final PsiPrimitiveType unboxed = PsiPrimitiveType.getUnboxedType(type);
    if (unboxed != null) type = unboxed;
    map.put(type.getPresentableText(), type);

    return map;
  }

  public static boolean isAppropriateContainerForIntroduceVariable(PsiElement tempContainer) {
    return tempContainer instanceof GrOpenBlock ||
        tempContainer instanceof GrClosableBlock ||
        tempContainer instanceof GroovyFileBase ||
        tempContainer instanceof GrCaseSection || tempContainer instanceof GrLoopStatement;
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
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    if (elements.length > 0) {
      highlightManager.addOccurrenceHighlights(editor, elements, attributes, false, highlighters);
    }
  }

  public static void highlightOccurrencesByRanges(Project project, Editor editor, TextRange[] ranges) {
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    for (TextRange range : ranges) {
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), attributes, false, highlighters);
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

  public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset, boolean strict) {
    if (!(file instanceof GroovyFileBase)) return PsiElement.EMPTY_ARRAY;
    Language language = GroovyFileType.GROOVY_FILE_TYPE.getLanguage();
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);

    if (element1 instanceof PsiWhiteSpace || isNewLine(element1)) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace || isNewLine(element2)) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset - 1);
    }
    if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;

    PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
    if (parent == null) return PsiElement.EMPTY_ARRAY;
    while (true) {
      if (parent instanceof GrCodeBlock) break;
      if (parent instanceof GroovyFileBase) break;
      if (parent instanceof GrCaseSection) break;
      if (parent instanceof GrStatement) {
        parent = parent.getParent();
        break;
      }
      if (parent == null) return PsiElement.EMPTY_ARRAY;
      final PsiElement prev = parent;
      parent = parent.getParent();
      if (parent instanceof GrCodeBlock && prev instanceof LeafPsiElement) { //braces
        parent = parent.getParent();
      }
    }

    if (!parent.equals(element1)) {
      while (!parent.equals(element1.getParent())) {
        element1 = element1.getParent();
      }
    }
    if (startOffset != element1.getTextRange().getStartOffset() && strict) return PsiElement.EMPTY_ARRAY;

    if (!parent.equals(element2)) {
      while (!parent.equals(element2.getParent())) {
        element2 = element2.getParent();
      }
    }
    if (endOffset != element2.getTextRange().getEndOffset() && strict) return PsiElement.EMPTY_ARRAY;

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
      if (child == element1) {
        flag = true;
      }
      if (flag) {
        possibleStatements.add(child);
      }
      if (child == element2) {
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
    if (!(statement instanceof GrConstructorInvocation)) return false;
    GrConstructorInvocation expr = (GrConstructorInvocation) statement;
    return (testForSuper && expr.isSuperCall()) || (testForThis && expr.isThisCall());
  }

  public static Collection<GrReturnStatement> findReturnStatements(PsiElement element) {
    ArrayList<GrReturnStatement> vector = new ArrayList<GrReturnStatement>();
    if (element instanceof GrMethod) {
      GrOpenBlock block = ((GrMethod) element).getBlock();
      if (block != null) {
        addReturnStatements(vector, block);
      }
    } else {
      addReturnStatements(vector, element);
    }
    return vector;
  }

  private static void addReturnStatements(ArrayList<GrReturnStatement> vector, PsiElement element) {
    if (element instanceof GrReturnStatement) {
      vector.add((GrReturnStatement) element);
    } else if (!(element instanceof PsiClass ||
        element instanceof GrClosableBlock)) {
      PsiElement[] children = element.getChildren();
      for (PsiElement child : children) {
        addReturnStatements(vector, child);
      }
    }
  }

  public static boolean hasWrongBreakStatements(PsiElement element) {
    ArrayList<GrBreakStatement> vector = new ArrayList<GrBreakStatement>();
    addBreakStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addBreakStatements(PsiElement element, ArrayList<GrBreakStatement> vector) {
    if (element instanceof GrBreakStatement) {
      vector.add(((GrBreakStatement) element));
    } else if (!(element instanceof GrLoopStatement ||
        element instanceof GrSwitchStatement ||
        element instanceof GrClosableBlock)) {
      for (PsiElement psiElement : element.getChildren()) {
        addBreakStatements(psiElement, vector);
      }
    }
  }

  public static boolean haswrongContinueStatements(PsiElement element) {
    ArrayList<GrContinueStatement> vector = new ArrayList<GrContinueStatement>();
    addContinueStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addContinueStatements(PsiElement element, ArrayList<GrContinueStatement> vector) {
    if (element instanceof GrContinueStatement) {
      vector.add(((GrContinueStatement) element));
    } else if (!(element instanceof GrLoopStatement || element instanceof GrClosableBlock)) {
      for (PsiElement psiElement : element.getChildren()) {
        addContinueStatements(psiElement, vector);
      }
    }
  }

  public static boolean hasTailReturnExpression(GrMethod method) {
    if (method.getReturnType() == PsiType.VOID) {
      return false;
    }
    GrOpenBlock body = method.getBlock();
    if (body == null) return false;
    GrStatement[] statements = body.getStatements();
    if (statements.length == 0) return false;
    GrStatement last = statements[statements.length - 1];
    return last instanceof GrExpression && PsiType.VOID != ((GrExpression) last).getType();
  }


  public static String getMethodSignature(PsiMethod method) {
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    String s = signature.getName() + "(";
    int i = 0;
    PsiType[] types = signature.getParameterTypes();
    for (PsiType type : types) {
      s += type.getPresentableText();
      if (i < types.length - 1) {
        s += ", ";
      }
      i++;
    }
    s += ")";
    return s;

  }

  @Nullable
  public static GrCall getCallExpressionByMethodReference(PsiElement ref) {
    if (ref instanceof GrEnumConstant) return (GrEnumConstant)ref;
    if (ref instanceof GrConstructorInvocation) return (GrCall)ref;
    PsiElement parent = ref.getParent();
    if (parent instanceof GrMethodCallExpression) {
      return (GrMethodCallExpression)parent;
    }
    else if (parent instanceof GrNewExpression) {
      return (GrNewExpression)parent;
    }
    else {
      return null;
    }
  }

  public static boolean isMethodUsage(PsiElement ref) {
    return (ref instanceof GrEnumConstant) || (ref.getParent() instanceof GrCall) || (ref instanceof GrConstructorInvocation);
  }

  public static String createTempVar(GrExpression expr, final GroovyPsiElement context, boolean declareFinal) {
    GrStatement anchorStatement = (GrStatement)getParentStatement(context, true);
    assert (anchorStatement != null && anchorStatement.getParent() != null);
//    LOG.assertTrue(anchorStatement != null && anchorStatement.getParent() != null);

    Project project = expr.getProject();
    String[] suggestedNames =GroovyNameSuggestionUtil.suggestVariableNames(expr, new NameValidator() {
      public String validateName(String name, boolean increaseNumber) {
        return name;
      }

      public Project getProject() {
        return context.getProject();
      }
    });
/*
      JavaCodeStyleManager.getInstance(project).suggestVariableName(VariableKind.LOCAL_VARIABLE, null, expr, null).names;*/
    final String prefix = suggestedNames[0];
    final String id = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(prefix, context, true);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expr.getProject());
    String[] modifiers;
    if (declareFinal) {
      modifiers = finalModifiers;
    }
    else {
      modifiers = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    GrVariableDeclaration decl =
      factory.createVariableDeclaration(modifiers, (GrExpression)skipParentheses(expr, false), expr.getType(), id);
/*    if (declareFinal) {
      com.intellij.psi.util.PsiUtil.setModifierProperty((decl.getMembers()[0]), PsiModifier.FINAL, true);
    }*/
    ((GrCodeBlock)anchorStatement.getParent()).addStatementBefore(decl, anchorStatement);

    return id;
  }

  @Nullable
  public static PsiElement getParentStatement(GroovyPsiElement place, boolean skipScopingStatements) {
    PsiElement parent = place;
    while (!(parent instanceof GrStatement)) {
      parent = parent.getParent();
      if (parent == null) return null;
    }
    PsiElement parentStatement = parent;
    parent = parentStatement.getParent();
    while (parent instanceof GrStatement) {
      if (!skipScopingStatements &&
          ((parent instanceof GrForStatement && parentStatement == ((GrForStatement)parent).getBody()) ||
           (parent instanceof GrWhileStatement && parentStatement == ((GrWhileStatement)parent).getBody()) ||
           (parent instanceof GrIfStatement &&
            (parentStatement == ((GrIfStatement)parent).getThenBranch() || parentStatement == ((GrIfStatement)parent).getElseBranch())))) {
        return parentStatement;
      }
      parentStatement = parent;
      parent = parent.getParent();
    }
    return parentStatement;
  }

  public static GrExpression convertJavaExpr2GroovyExpr(PsiElement expr) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(expr.getProject());

    final List<PsiLocalVariable> localVariables = new ArrayList<PsiLocalVariable>();
    final List<PsiField> fields = new ArrayList<PsiField>();
    final List<PsiParameter> parameters = new ArrayList<PsiParameter>();

    expr.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        final PsiExpression qualifierExpression = expression.getQualifierExpression();
        if (qualifierExpression != null && !(qualifierExpression instanceof PsiThisExpression)) return;

        PsiElement el = expression.resolve();
        if (el instanceof PsiField) {
          fields.add((PsiField)el);
        }
        else if (el instanceof PsiParameter) {
          parameters.add((PsiParameter)el);
        }
        else if (el instanceof PsiLocalVariable) {
          localVariables.add((PsiLocalVariable)el);
        }
        super.visitReferenceExpression(expression);
      }
    });

    PsiJavaFile file = (PsiJavaFile)expr.getContainingFile();

    StringBuilder cf = new StringBuilder();

    final PsiPackageStatement packageStatement = file.getPackageStatement();
    if (packageStatement != null) cf.append(packageStatement.getText());

    final PsiImportList importList = file.getImportList();
    if (importList != null) cf.append(importList.getText());

    cf.append("class A{");
    for (PsiField field : fields) {
      cf.append(field.getText());
    }

    cf.append("void foo(");
    for (int i = 0, parametersSize = parameters.size() - 1; i < parametersSize; i++) {
      PsiParameter parameter = parameters.get(i);
      cf.append(parameter.getText()).append(',');
    }
    if (parameters.size() > 0) {
      cf.append(parameters.get(parameters.size() - 1).getText());
    }
    cf.append("){");
    for (PsiLocalVariable localVariable : localVariables) {
      cf.append(localVariable.getText());
    }
    cf.append("Object _________________ooooooo_______________=");
    cf.append(expr.getText());
    cf.append(";}}");
    final GroovyFile grFile = factory.createGroovyFile(cf.toString(), false, expr);

    final GrMethod method = (GrMethod)grFile.getClasses()[0].getMethods()[0];
    final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)method.getBlock().getStatements()[0];
    return variableDeclaration.getVariables()[0].getInitializerGroovy();
  }

  public static int verifySafeCopyExpression(GrExpression expression) {
    return verifySafeCopyExpressionSubElement(expression);
  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof GrThisReferenceExpression || element instanceof GrSuperReferenceExpression || element instanceof GrNamedElement) {
      return EXPR_COPY_SAFE;
    }

    if (element instanceof GrMethodCallExpression) {
      result = EXPR_COPY_UNSAFE;
    }

    if (element instanceof GrNewExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrAssignmentExpression) {
      return EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrClosableBlock) {
      return EXPR_COPY_PROHIBITED;
    }

    if (isPlusPlusOrMinusMinus(element)) {
      return EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
  }

  public static boolean isPlusPlusOrMinusMinus(PsiElement element) {
    if (element instanceof GrUnaryExpression) {
      IElementType operandSign = ((GrUnaryExpression)element).getOperationTokenType();
      return operandSign == GroovyTokenTypes.mDEC || operandSign == GroovyTokenTypes.mINC;
    }
    return false;
  }
}
