// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class TrivialFunctionalExpressionUsageInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodReferenceExpression(final PsiMethodReferenceExpression expression) {
        doCheckMethodCallOnFunctionalExpression(expression, call -> expression.resolve() != null);
      }

      @Override
      public void visitLambdaExpression(final PsiLambdaExpression expression) {
        final PsiElement body = expression.getBody();
        if (body == null) return;

        Predicate<PsiMethodCallExpression> checkBody = call -> {
          final PsiElement callParent = call.getParent();

          if (!(body instanceof PsiCodeBlock)) {
            return callParent instanceof PsiStatement || callParent instanceof PsiLocalVariable || expression.isValueCompatible();
          }

          PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
          if (statements.length == 1) {
            return callParent instanceof PsiStatement
                   || callParent instanceof PsiLocalVariable
                   || statements[0] instanceof PsiReturnStatement && expression.isValueCompatible();
          }

          final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements((PsiCodeBlock)body);
          if (returnStatements.length > 1) {
            return false;
          }

          if (returnStatements.length == 1) {
            if (!(ArrayUtil.getLastElement(statements) instanceof PsiReturnStatement)) {
              return false;
            }
          }

          return CodeBlockSurrounder.canSurround(call);
        };
        Predicate<PsiMethodCallExpression> checkWrites = call ->
          !ContainerUtil.exists(expression.getParameterList().getParameters(), parameter -> VariableAccessUtils.variableIsAssigned(parameter, body));

        doCheckMethodCallOnFunctionalExpression(expression, checkBody.and(checkWrites));
      }

      @Override
      public void visitAnonymousClass(final PsiAnonymousClass aClass) {
        if (AnonymousCanBeLambdaInspection.canBeConvertedToLambda(aClass, false, Collections.emptySet())) {
          final PsiNewExpression newExpression = ObjectUtils.tryCast(aClass.getParent(), PsiNewExpression.class);
          doCheckMethodCallOnFunctionalExpression(call -> {
            final PsiMethod method = aClass.getMethods()[0];
            final PsiCodeBlock body = method.getBody();
            final PsiReturnStatement[] returnStatements = PsiUtil.findReturnStatements(body);
            if (returnStatements.length > 1) {
              return false;
            }
            final PsiElement callParent = PsiUtil.skipParenthesizedExprUp(call.getParent());
            return callParent instanceof PsiStatement ||
                   callParent instanceof PsiLocalVariable;
          }, newExpression, aClass.getBaseClassType(), new ReplaceAnonymousWithLambdaBodyFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(PsiElement expression,
                                                           Predicate<? super PsiMethodCallExpression> elementContainerPredicate) {
        final PsiTypeCastExpression parent =
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(expression.getParent()), PsiTypeCastExpression.class);
        if (parent != null) {
          final PsiType interfaceType = parent.getType();
          doCheckMethodCallOnFunctionalExpression(elementContainerPredicate, parent, interfaceType,
                                                  expression instanceof PsiLambdaExpression ? new ReplaceWithLambdaBodyFix()
                                                                                             : new ReplaceWithMethodReferenceFix());
        }
      }

      private void doCheckMethodCallOnFunctionalExpression(Predicate<? super PsiMethodCallExpression> elementContainerPredicate,
                                                           PsiExpression qualifier,
                                                           PsiType interfaceType,
                                                           LocalQuickFix fix) {
        final PsiMethodCallExpression call = ExpressionUtils.getCallForQualifier(qualifier);
        if (call == null) return;

        final PsiMethod method = call.resolveMethod();
        final PsiElement referenceNameElement = call.getMethodExpression().getReferenceNameElement();
        boolean suitableMethod = method != null &&
                                 referenceNameElement != null &&
                                 !method.isVarArgs() &&
                                 call.getArgumentList().getExpressionCount() == method.getParameterList().getParametersCount() &&
                                 elementContainerPredicate.test(call);
        if (!suitableMethod) return;
        final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(interfaceType);
        if (method == interfaceMethod || interfaceMethod != null && MethodSignatureUtil.isSuperMethod(interfaceMethod, method)) {
          holder.registerProblem(referenceNameElement,
                                 InspectionGadgetsBundle.message("inspection.trivial.functional.expression.usage.description"), fix);
        }
      }
    };
  }

  private static void replaceWithLambdaBody(PsiLambdaExpression lambda) {
    lambda = extractSideEffects(lambda);
    if (lambda == null) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    PsiElement body = lambda.getBody();
    PsiExpression expression = LambdaUtil.extractSingleExpressionFromBody(body);
    if (expression != null) {
      replaceExpression(callExpression, lambda);
    }
    else if (body instanceof PsiCodeBlock) {
      replaceCodeBlock(lambda);
    }
  }

  private static PsiLambdaExpression extractSideEffects(PsiLambdaExpression lambda) {
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    PsiExpression[] arguments = callExpression.getArgumentList().getExpressions();
    if (!ContainerUtil.exists(arguments, SideEffectChecker::mayHaveSideEffects)) return lambda;

    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(lambda);
    if (surrounder == null) return null;
    lambda = (PsiLambdaExpression)surrounder.surround().getExpression();
    callExpression = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
    if (callExpression == null) return lambda;
    arguments = callExpression.getArgumentList().getExpressions();
    PsiParameter[] parameters = lambda.getParameterList().getParameters();
    if (arguments.length != parameters.length) return lambda;
    PsiStatement anchor = PsiTreeUtil.getParentOfType(lambda, PsiStatement.class, false);
    if (anchor == null) return lambda;

    List<PsiStatement> statements = new ArrayList<>();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(lambda.getProject());

    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      PsiParameter parameter = parameters[i];
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(argument);
      if (!sideEffects.isEmpty()) {
        boolean used = VariableAccessUtils.variableIsUsed(parameter, lambda.getBody());
        if (used) {
          String name = parameter.getName();
          statements.add(
            factory.createStatementFromText(parameter.getType().getCanonicalText() + " " + name + "=" + argument.getText()+";", lambda));
          argument.replace(factory.createExpressionFromText(name, parameter));
        }
        else {
          Collections.addAll(statements, StatementExtractor.generateStatements(sideEffects, argument));
        }
      }
    }
    BlockUtils.addBefore(anchor, statements.toArray(PsiStatement.EMPTY_ARRAY));
    return lambda;
  }

  private static void replaceExpression(PsiMethodCallExpression callExpression, PsiLambdaExpression element) {
    PsiExpression expression;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    // body could be invalidated after inlining
    expression = LambdaUtil.extractSingleExpressionFromBody(element.getBody());
    ct.replaceAndRestoreComments(callExpression, expression);
  }

  private static void replaceCodeBlock(PsiLambdaExpression element) {
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(element);
    if (surrounder == null) return;
    CodeBlockSurrounder.SurroundResult result = surrounder.surround();
    element = (PsiLambdaExpression)result.getExpression();
    PsiElement body = element.getBody();
    if (!(body instanceof PsiCodeBlock)) return;
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
    if (callExpression == null) return;
    final CommentTracker ct = new CommentTracker();
    inlineCallArguments(callExpression, element, ct);
    body = element.getBody();
    final PsiElement parent = callExpression.getParent();
    final PsiStatement[] statements = ((PsiCodeBlock)body).getStatements();
    PsiReturnStatement statement = null;
    if (statements.length > 0) {
      PsiElement anchor = result.getAnchor();
      statement = ObjectUtils.tryCast(statements[statements.length - 1], PsiReturnStatement.class);
      PsiElement gParent = anchor.getParent();
      if (hasNameConflict(statements, anchor, element)) {
        gParent.addBefore(JavaPsiFacade.getElementFactory(element.getProject()).createStatementFromText(ct.text(body), anchor), anchor);
      }
      else {
        for (PsiElement child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
          if (child != statement && !(child instanceof PsiJavaToken)) {
            gParent.addBefore(ct.markUnchanged(child), anchor);
          }
        }
      }
    }
    final PsiExpression returnValue = statement == null ? null : statement.getReturnValue();
    if (returnValue != null) {
      ct.replaceAndRestoreComments(callExpression, returnValue);
    }
    else {
      if (parent instanceof PsiExpressionStatement) {
        ct.deleteAndRestoreComments(callExpression);
      }
      else {
        ct.deleteAndRestoreComments(parent);
      }
    }
  }
  
  private static boolean hasNameConflict(PsiStatement[] statements, PsiElement anchor, PsiLambdaExpression lambda) {
    Predicate<PsiVariable> allowedVar = variable -> PsiTreeUtil.isAncestor(lambda, variable, true);
    JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(anchor.getProject());
    return StreamEx.of(statements).select(PsiDeclarationStatement.class)
      .flatArray(PsiDeclarationStatement::getDeclaredElements)
      .select(PsiNamedElement.class)
      .map(PsiNamedElement::getName)
      .nonNull()
      .anyMatch(name -> !name.equals(manager.suggestUniqueVariableName(name, anchor, allowedVar)));
  }

  private static void inlineCallArguments(PsiMethodCallExpression callExpression,
                                          PsiLambdaExpression element,
                                          CommentTracker ct) {
    final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
    final PsiParameter[] parameters = element.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final PsiExpression initializer = args[i];
      for (PsiReference reference : ReferencesSearch.search(parameter)) {
        final PsiElement referenceElement = reference.getElement();
        if (referenceElement instanceof PsiJavaCodeReferenceElement) {
          ct.markUnchanged(initializer);
          JavaSpecialRefactoringProvider.getInstance()
            .inlineVariable(parameter, initializer, (PsiJavaCodeReferenceElement)referenceElement, null);
        }
      }
    }
  }

  private static class ReplaceWithLambdaBodyFix extends ReplaceFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.lambda.body.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = PsiUtil.skipParenthesizedExprDown(((PsiTypeCastExpression)qualifierExpression).getOperand());
        if (element instanceof PsiLambdaExpression) {
          replaceWithLambdaBody((PsiLambdaExpression)element);
        }
      }
    }
  }

  private static class ReplaceWithMethodReferenceFix extends ReplaceFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.method.reference.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      if (qualifierExpression instanceof PsiTypeCastExpression) {
        final PsiExpression element = ((PsiTypeCastExpression)qualifierExpression).getOperand();
        if (element instanceof PsiMethodReferenceExpression) {
          final PsiLambdaExpression lambdaExpression =
            LambdaRefactoringUtil.convertMethodReferenceToLambda((PsiMethodReferenceExpression)element, false, true);
          if (lambdaExpression != null) {
            replaceWithLambdaBody(lambdaExpression);
          }
        }
      }
    }
  }

  private static class ReplaceAnonymousWithLambdaBodyFix extends ReplaceFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.anonymous.with.lambda.body.fix.family.name");
    }

    @Override
    protected void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression) {
      final PsiExpression cast = AnonymousCanBeLambdaInspection.replacePsiElementWithLambda(qualifierExpression, true, false);
      if (cast instanceof PsiTypeCastExpression) {
        final PsiExpression lambdaExpression = ((PsiTypeCastExpression)cast).getOperand();
        if (lambdaExpression instanceof PsiLambdaExpression) {
          replaceWithLambdaBody((PsiLambdaExpression)lambdaExpression);
        }
      }
    }
  }

  private static abstract class ReplaceFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement psiElement = descriptor.getPsiElement();
      final PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(psiElement, PsiMethodCallExpression.class);
      if (callExpression != null) {
        fixExpression(callExpression, PsiUtil.skipParenthesizedExprDown(callExpression.getMethodExpression().getQualifierExpression()));
      }
    }

    protected abstract void fixExpression(PsiMethodCallExpression callExpression, PsiExpression qualifierExpression);
  }
}
