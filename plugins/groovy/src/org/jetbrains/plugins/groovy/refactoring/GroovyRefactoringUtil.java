// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.refactoring;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.*;
import java.util.function.BiPredicate;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isPlusPlusOrMinusMinus;

public abstract class GroovyRefactoringUtil {
  private static final Logger LOG = Logger.getInstance(GroovyRefactoringUtil.class);

  public static final Collection<String> KEYWORDS = ContainerUtil.map(TokenSets.KEYWORDS.getTypes(), Object::toString);

  private static final String[] finalModifiers = new String[]{PsiModifier.FINAL};

  public static PsiElement[] getExpressionOccurrences(@NotNull PsiElement expr, @NotNull PsiElement scope) {
    ArrayList<PsiElement> occurrences = new ArrayList<>();
    BiPredicate<PsiElement, PsiElement> comparator = (element1, element2) -> {
      if (element1 != null && element1.equals(element2)) return true;

      return element1 instanceof GrParameter param1 &&
             element2 instanceof GrParameter param2 && 
             param1.getName().equals(param2.getName());
    };

    if (scope instanceof GrLoopStatement) {
      PsiElement son = expr;
      while (son.getParent() != null && !(son.getParent() instanceof GrLoopStatement)) {
        son = son.getParent();
      }
      assert scope.equals(son.getParent());
      collectOccurrences(expr, son, occurrences, comparator, false);
    } else {
      collectOccurrences(expr, scope, occurrences, comparator, scope instanceof GrTypeDefinition || scope instanceof GroovyFileBase);
    }
    return PsiUtilCore.toPsiElementArray(occurrences);
  }


  private static void collectOccurrences(@NotNull PsiElement expr, @NotNull PsiElement scope, @NotNull ArrayList<? super PsiElement> acc, BiPredicate<PsiElement, PsiElement> comparator, boolean goIntoInner) {
    if (scope.equals(expr)) {
      acc.add(expr);
      return;
    }
    for (PsiElement child : scope.getChildren()) {
      if (goIntoInner || !(child instanceof GrTypeDefinition) && !(child instanceof GrMethod && scope instanceof GroovyFileBase)) {
        if (PsiEquivalenceUtil.areEquivalent(child, expr, comparator, false)) {
          acc.add(child);
        } else {
          collectOccurrences(expr, child, acc, comparator, goIntoInner);
        }
      }
    }
  }


  public static boolean isAppropriateContainerForIntroduceVariable(PsiElement tempContainer) {
    return tempContainer instanceof GrOpenBlock ||
        tempContainer instanceof GrClosableBlock ||
        tempContainer instanceof GroovyFileBase ||
        tempContainer instanceof GrCaseSection ||
        tempContainer instanceof GrLoopStatement ||
        tempContainer instanceof GrIfStatement;
  }

  public static void sortOccurrences(PsiElement[] occurrences) {
    Arrays.sort(occurrences, (elem1, elem2) -> {
      final int offset1 = elem1.getTextRange().getStartOffset();
      final int offset2 = elem2.getTextRange().getStartOffset();
      return offset1 - offset2;
    });
  }


  public static void highlightOccurrences(Project project, @Nullable Editor editor, PsiElement[] elements) {
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    if (elements.length > 0) {
      highlightManager.addOccurrenceHighlights(editor, elements, EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
    }
  }

  public static void highlightOccurrencesByRanges(Project project, Editor editor, TextRange[] ranges) {
    if (editor == null) return;
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    for (TextRange range : ranges) {
      highlightManager.addRangeHighlight(editor, range.getStartOffset(), range.getEndOffset(), 
                                         EditorColors.SEARCH_RESULT_ATTRIBUTES, false, highlighters);
    }
  }

  public static PsiElement @NotNull [] findStatementsInRange(PsiFile file, int startOffset, int endOffset, boolean strict) {
    if (!(file instanceof GroovyFileBase)) return PsiElement.EMPTY_ARRAY;
    Language language = GroovyLanguage.INSTANCE;
    PsiElement element1 = file.getViewProvider().findElementAt(startOffset, language);
    PsiElement element2 = file.getViewProvider().findElementAt(endOffset - 1, language);

    if (element1 instanceof PsiWhiteSpace || org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isNewLine(element1)) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace || org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isNewLine(element2)) {
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

    // calculate children
    PsiElement[] children = PsiElement.EMPTY_ARRAY;
    PsiElement psiChild = parent.getFirstChild();
    if (psiChild != null) {
      List<PsiElement> result = new ArrayList<>();
      while (psiChild != null) {
        result.add(psiChild);
        psiChild = psiChild.getNextSibling();
      }
      children = PsiUtilCore.toPsiElementArray(result);
    }


    ArrayList<PsiElement> possibleStatements = new ArrayList<>();
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

    return PsiUtilCore.toPsiElementArray(possibleStatements);
  }

  public static boolean isSuperOrThisCall(GrStatement statement, boolean testForSuper, boolean testForThis) {
    if (!(statement instanceof GrConstructorInvocation expr)) return false;
    return (testForSuper && expr.isSuperCall()) || (testForThis && expr.isThisCall());
  }

  public static boolean hasWrongBreakStatements(PsiElement element) {
    ArrayList<GrBreakStatement> vector = new ArrayList<>();
    addBreakStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addBreakStatements(PsiElement element, ArrayList<? super GrBreakStatement> vector) {
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

  public static boolean hasWrongContinueStatements(PsiElement element) {
    ArrayList<GrContinueStatement> vector = new ArrayList<>();
    addContinueStatements(element, vector);
    return !vector.isEmpty();
  }

  private static void addContinueStatements(PsiElement element, ArrayList<? super GrContinueStatement> vector) {
    if (element instanceof GrContinueStatement) {
      vector.add(((GrContinueStatement) element));
    } else if (!(element instanceof GrLoopStatement || element instanceof GrClosableBlock)) {
      for (PsiElement psiElement : element.getChildren()) {
        addContinueStatements(psiElement, vector);
      }
    }
  }


  public static @NlsSafe String getMethodSignature(PsiMethod method) {
    MethodSignature signature = method.getSignature(PsiSubstitutor.EMPTY);
    StringBuilder s = new StringBuilder(signature.getName() + "(");
    int i = 0;
    PsiType[] types = signature.getParameterTypes();
    for (PsiType type : types) {
      s.append(type.getPresentableText());
      if (i < types.length - 1) {
        s.append(", ");
      }
      i++;
    }
    s.append(")");
    return s.toString();

  }

  @Nullable
  public static GrCall getCallExpressionByMethodReference(@Nullable PsiElement ref) {
    if (ref == null) return null;
    if (ref instanceof GrEnumConstant) return (GrEnumConstant)ref;
    if (ref instanceof GrConstructorInvocation) return (GrCall)ref;
    PsiElement parent = ref.getParent();
    if (parent instanceof GrCall) {
      return (GrCall)parent;
    }
    else if (parent instanceof GrAnonymousClassDefinition) {
      return (GrCall)parent.getParent();
    }
    else {
      return null;
    }
  }

  public static boolean isMethodUsage(PsiElement ref) {
    return (ref instanceof GrEnumConstant) || (ref.getParent() instanceof GrCall) || (ref instanceof GrConstructorInvocation);
  }

  public static String createTempVar(GrExpression expr, final GroovyPsiElement context, boolean declareFinal) {
    expr = addBlockIntoParent(expr);
    final GrVariableDeclarationOwner block = PsiTreeUtil.getParentOfType(expr, GrVariableDeclarationOwner.class);
    LOG.assertTrue(block != null);
    final PsiElement anchorStatement = PsiTreeUtil.findPrevParent(block, expr);
    LOG.assertTrue(anchorStatement instanceof GrStatement);

    Project project = expr.getProject();
    String[] suggestedNames =GroovyNameSuggestionUtil.suggestVariableNames(expr, new NameValidator() {
      @Override
      public String validateName(String name, boolean increaseNumber) {
        return name;
      }

      @Override
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
      modifiers = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
    GrVariableDeclaration decl =
      factory.createVariableDeclaration(modifiers, (GrExpression)org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
        .skipParentheses(expr, false), expr.getType(), id);
    final GrStatement statement = ((GrStatementOwner)anchorStatement.getParent()).addStatementBefore(decl, (GrStatement)anchorStatement);
    JavaCodeStyleManager.getInstance(statement.getProject()).shortenClassReferences(statement);

    return id;
  }

  public static int verifySafeCopyExpression(GrExpression expression) {
    return verifySafeCopyExpressionSubElement(expression);
  }

  private static int verifySafeCopyExpressionSubElement(PsiElement element) {
    int result = RefactoringUtil.EXPR_COPY_SAFE;
    if (element == null) return result;

    if (element instanceof GrNamedElement) {
      return RefactoringUtil.EXPR_COPY_SAFE;
    }

    if (element instanceof GrMethodCallExpression) {
      result = RefactoringUtil.EXPR_COPY_UNSAFE;
    }

    if (element instanceof GrNewExpression) {
      return RefactoringUtil.EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrAssignmentExpression) {
      return RefactoringUtil.EXPR_COPY_PROHIBITED;
    }

    if (element instanceof GrClosableBlock) {
      return RefactoringUtil.EXPR_COPY_PROHIBITED;
    }

    if (isPlusPlusOrMinusMinus(element)) {
      return RefactoringUtil.EXPR_COPY_PROHIBITED;
    }

    PsiElement[] children = element.getChildren();

    for (PsiElement child : children) {
      int childResult = verifySafeCopyExpressionSubElement(child);
      result = Math.max(result, childResult);
    }
    return result;
  }

  public static boolean isCorrectReferenceName(String newName, Project project) {
    if (newName.startsWith("'''") || newName.startsWith("\"\"\"")) {
      if (newName.length() < 6 || !newName.endsWith("'''")) {
        return false;
      }
    }
    else if (StringUtil.startsWithChar(newName, '\'') || StringUtil.startsWithChar(newName, '"')) {
      if (newName.length() < 2 || !newName.endsWith("'")) {
        return false;
      }
    }
    if (KEYWORDS.contains(newName)) {
      return false;
    }
    try {
      GroovyPsiElementFactory.getInstance(project).createReferenceNameFromText(newName);
    }
    catch (IncorrectOperationException e) {
      return false;
    }
    return true;
  }

  public static GrExpression generateArgFromMultiArg(PsiSubstitutor substitutor,
                                                     List<? extends PsiElement> arguments,
                                                     @Nullable PsiType type,
                                                     final Project project) {
    StringBuilder argText = new StringBuilder();
    argText.append("[");
    for (PsiElement argument : arguments) {
      argText.append(argument.getText()).append(", ");
    }
    if (!arguments.isEmpty()) {
      argText.delete(argText.length() - 2, argText.length());
    }
    argText.append("]");
    if (type instanceof PsiArrayType) {
      type = substitutor.substitute(type);
      String typeText = type.getCanonicalText();
      if (type instanceof PsiEllipsisType) {
        typeText = typeText.replace("...", "[]");
      }
      argText.append(" as ").append(typeText);
    }
    return GroovyPsiElementFactory.getInstance(project).createExpressionFromText(argText.toString());
  }

  public static boolean hasSideEffect(@NotNull GroovyPsiElement statement) {
    final Ref<Boolean> hasSideEffect = new Ref<>(false);
    statement.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitCallExpression(@NotNull GrCallExpression callExpression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitClosure(@NotNull GrClosableBlock closure) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
        hasSideEffect.set(true);
      }

      @Override
      public void visitElement(@NotNull GroovyPsiElement element) {
        if (hasSideEffect.get()) return;
        super.visitElement(element);
      }
    });

    return hasSideEffect.get();
  }

  /**
   *  adds block statement in parent of statement if needed. For Example:
   *    while (true) a=foo()
   *  will be replaced with
   *    while(true) {a=foo()}
   * @return corresponding statement inside block if it has been created or statement itself.
   */

  @NotNull
  public static <Type extends PsiElement> Type addBlockIntoParent(@NotNull Type statement) throws IncorrectOperationException {

    PsiElement parent = statement.getParent();
    PsiElement child = statement;
    while (!(parent instanceof GrLoopStatement) &&
           !(parent instanceof GrIfStatement) &&
           !(parent instanceof GrVariableDeclarationOwner) &&
           parent != null) {
      parent = parent.getParent();
      child = child.getParent();
    }
    if (parent instanceof GrWhileStatement && child == ((GrWhileStatement)parent).getCondition() ||
        parent instanceof GrIfStatement && child == ((GrIfStatement)parent).getCondition()) {
      parent = parent.getParent();
    }
    assert parent != null;
    if (parent instanceof GrVariableDeclarationOwner) {
      return statement;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(statement.getProject());
    PsiElement tempStmt = statement;
    while (parent != tempStmt.getParent()) {
      tempStmt = tempStmt.getParent();
    }
    GrStatement toAdd = (GrStatement)tempStmt.copy();
    GrBlockStatement blockStatement = factory.createBlockStatement();
    if (parent instanceof GrLoopStatement) {
      ((GrLoopStatement)parent).replaceBody(blockStatement);
    }
    else {
      GrIfStatement ifStatement = (GrIfStatement)parent;
      if (tempStmt == ifStatement.getThenBranch()) {
        ifStatement.replaceThenBranch(blockStatement);
      }
      else if (tempStmt == ifStatement.getElseBranch()) {
        ifStatement.replaceElseBranch(blockStatement);
      }
    }
    GrStatement result = blockStatement.getBlock().addStatementBefore(toAdd, null);
    if (result instanceof GrReturnStatement) {
      // noinspection unchecked
      statement = (Type)((GrReturnStatement)result).getReturnValue();
    }
    else {
      //noinspection unchecked
      statement = (Type)result;
    }

    return statement;
  }

  public static boolean isDiamondNewOperator(GrExpression expression) {
    PsiElement element = org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses(expression, false);
    if (!(element instanceof GrNewExpression)) return false;
    if (((GrNewExpression)element).getArrayCount() > 0) return false;

    GrCodeReferenceElement referenceElement = ((GrNewExpression)element).getReferenceElement();
    if (referenceElement == null) return false;

    GrTypeArgumentList typeArgumentList = referenceElement.getTypeArgumentList();
    return typeArgumentList != null && typeArgumentList.isDiamond();
  }

  @Nullable
  public static GrStatementOwner getDeclarationOwner(GrStatement statement) {
    PsiElement parent = statement.getParent();
    return parent instanceof GrStatementOwner ? ((GrStatementOwner) parent) : null;
  }

  @NotNull
  private static PsiType getType(@NotNull PsiParameter myParameter) {
    PsiType type = myParameter.getType();
    return type instanceof PsiEllipsisType ? ((PsiEllipsisType)type).toArrayType() : type;
  }

  @NotNull
  public static PsiType getSubstitutedType(@NotNull GrParameter parameter) {
    final PsiType type = getType(parameter);

    if (type instanceof PsiArrayType) {
      return type;
    }

    final PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
    final PsiClass psiClass = result.getElement();
    if (psiClass == null) return type;
    final HashSet<PsiTypeParameter> usedTypeParameters = new HashSet<>();
    collectTypeParameters(usedTypeParameters, parameter);
    for (Iterator<PsiTypeParameter> iterator = usedTypeParameters.iterator(); iterator.hasNext(); ) {
      PsiTypeParameter usedTypeParameter = iterator.next();
      if (parameter.getDeclarationScope() != usedTypeParameter.getOwner()) {
        iterator.remove();
      }
    }
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(parameter.getProject());
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    for (PsiTypeParameter usedTypeParameter : usedTypeParameters) {
      subst = subst.put(usedTypeParameter, TypeConversionUtil.typeParameterErasure(usedTypeParameter));
    }
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final Map<PsiTypeParameter, PsiType> typeMap = result.getSubstitutor().getSubstitutionMap();
    for (PsiTypeParameter typeParameter : typeMap.keySet()) {
      final PsiType psiType = typeMap.get(typeParameter);
      substitutor = substitutor.put(typeParameter, psiType != null ? subst.substitute(psiType) : null);
    }
    return psiClass instanceof PsiTypeParameter ? subst.substitute((PsiTypeParameter)psiClass) : elementFactory.createType(psiClass, substitutor);
  }

  public static void collectTypeParameters(final Set<? super PsiTypeParameter> used, @NotNull final GroovyPsiElement element) {
    element.accept(new GroovyRecursiveElementVisitor() {
      @Override
      public void visitCodeReferenceElement(@NotNull GrCodeReferenceElement reference) {
        super.visitCodeReferenceElement(reference);
        if (reference.getQualifier() == null) {
          final PsiElement resolved = reference.resolve();
          if (resolved instanceof PsiTypeParameter typeParameter) {
            if (PsiTreeUtil.isAncestor(typeParameter.getOwner(), element, false)) {
              used.add(typeParameter);
            }
          }
        }
      }

      @Override
      public void visitExpression(@NotNull final GrExpression expression) {
        super.visitExpression(expression);
        final PsiType type = expression.getType();
        if (type != null) {
          final TypeParameterSearcher searcher = new TypeParameterSearcher();
          type.accept(searcher);
          for (PsiTypeParameter typeParam : searcher.myTypeParams) {
            if (PsiTreeUtil.isAncestor(typeParam.getOwner(), element, false)) {
              used.add(typeParam);
            }
          }
        }
      }

      class TypeParameterSearcher extends PsiTypeVisitor<Boolean> {
        private final Set<PsiTypeParameter> myTypeParams = new HashSet<>();

        @Override
        public Boolean visitType(@NotNull final PsiType type) {
          return false;
        }

        @Override
        public Boolean visitArrayType(@NotNull final PsiArrayType arrayType) {
          return arrayType.getComponentType().accept(this);
        }

        @Override
        public Boolean visitClassType(@NotNull final PsiClassType classType) {
          final PsiClass aClass = classType.resolve();
          if (aClass instanceof PsiTypeParameter) {
            myTypeParams.add((PsiTypeParameter)aClass);
          }

          final PsiType[] types = classType.getParameters();
          for (final PsiType psiType : types) {
            psiType.accept(this);
          }
          return false;
        }

        @Override
        public Boolean visitWildcardType(@NotNull final PsiWildcardType wildcardType) {
          final PsiType bound = wildcardType.getBound();
          if (bound != null) {
            bound.accept(this);
          }
          return false;
        }
      }
    });
  }

  @NotNull
  public static String getNewName(@NotNull PsiNamedElement element, boolean property) {
    final String name;
    if (element instanceof PsiMethod && property) {
      name = GroovyPropertyUtils.getPropertyName((PsiMethod)element);
    }
    else {
      name = element.getName();
    }
    LOG.assertTrue(name != null, element);
    return name;
  }
}
