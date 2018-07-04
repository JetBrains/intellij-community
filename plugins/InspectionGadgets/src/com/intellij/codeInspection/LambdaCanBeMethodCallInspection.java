// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class LambdaCanBeMethodCallInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        super.visitLambdaExpression(lambda);
        PsiElement body = lambda.getBody();
        if (body == null) return;
        PsiType type = lambda.getFunctionalInterfaceType();
        if (!(type instanceof PsiClassType)) return;
        PsiElement parent = PsiUtil.skipParenthesizedExprUp(lambda.getParent());
        if(parent instanceof PsiTypeCastExpression &&
           InheritanceUtil.isInheritor(((PsiTypeCastExpression)parent).getType(), CommonClassNames.JAVA_IO_SERIALIZABLE)) return;
        PsiExpression expression = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(body));
        if (expression == null) return;
        PsiParameter[] parameters = lambda.getParameterList().getParameters();
        if (parameters.length == 1) {
          PsiParameter parameter = parameters[0];
          if (ExpressionUtils.isReferenceTo(expression, parameter)) {
            processFunctionIdentity(lambda, (PsiClassType)type);
          }
          if (expression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
            PsiClass aClass = ((PsiClassType)type).resolve();
            if (aClass != null && CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE.equals(aClass.getQualifiedName())) {
              handlePredicateIsEqual(lambda, parameter, call);
              handlePatternAsPredicate(lambda, parameter, call);
            }
          }
        }
      }

      private void processFunctionIdentity(PsiLambdaExpression lambda, PsiClassType type) {
        PsiClass aClass = type.resolve();
        if (aClass == null || !CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION.equals(aClass.getQualifiedName())) return;
        PsiType[] typeParameters = type.getParameters();
        if (typeParameters.length != 2 || !typeParameters[1].isAssignableFrom(typeParameters[0])) return;
        String replacement = CommonClassNames.JAVA_UTIL_FUNCTION_FUNCTION + ".identity()";
        if (!LambdaUtil.isSafeLambdaReplacement(lambda, replacement)) return;
        registerProblem(lambda, "Function.identity()", replacement);
      }

      private void handlePatternAsPredicate(PsiLambdaExpression lambda, PsiParameter parameter, PsiMethodCallExpression call) {
        if (MethodCallUtils.isCallToMethod(call, "java.util.regex.Matcher", PsiType.BOOLEAN, "find")) {
          PsiExpression matcher = call.getMethodExpression().getQualifierExpression();
          if (matcher instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression matcherCall = (PsiMethodCallExpression)matcher;
            if (MethodCallUtils.isCallToMethod(matcherCall, "java.util.regex.Pattern", null, "matcher",
                                               new PsiType[]{null})) {
              PsiExpression[] matcherArgs = matcherCall.getArgumentList().getExpressions();
              if (matcherArgs.length == 1 && ExpressionUtils.isReferenceTo(matcherArgs[0], parameter)) {
                PsiExpression pattern = matcherCall.getMethodExpression().getQualifierExpression();
                if (pattern != null && LambdaCanBeMethodReferenceInspection.checkQualifier(pattern)) {
                  registerProblem(lambda, "Pattern.asPredicate()",
                                  ParenthesesUtils.getText(pattern, ParenthesesUtils.POSTFIX_PRECEDENCE) + ".asPredicate()");
                }
              }
            }
          }
        }
      }

      private void handlePredicateIsEqual(PsiLambdaExpression lambda, PsiParameter parameter, PsiMethodCallExpression call) {
        if (MethodCallUtils.isCallToStaticMethod(call, "java.util.Objects", "equals", 2)) {
          PsiExpression[] args = call.getArgumentList().getExpressions();
          if (args.length == 2) {
            PsiExpression comparedWith;
            if (ExpressionUtils.isReferenceTo(args[0], parameter)) {
              comparedWith = args[1];
            }
            else if (ExpressionUtils.isReferenceTo(args[1], parameter)) {
              comparedWith = args[0];
            }
            else return;
            if (LambdaCanBeMethodReferenceInspection.checkQualifier(comparedWith)) {
              registerProblem(lambda, "Predicate.isEqual()",
                              CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE + ".isEqual(" + comparedWith.getText() + ")");
            }
          }
        }
      }

      private void registerProblem(PsiLambdaExpression lambda, String displayReplacement, String replacement) {
        holder.registerProblem(lambda, InspectionsBundle.message("inspection.lambda.to.method.call.message", displayReplacement),
                               new ReplaceWithFunctionCallFix(replacement, displayReplacement));
      }
    };
  }

  static final class ReplaceWithFunctionCallFix implements LocalQuickFix {
    private final String myDisplayReplacement;
    private final String myReplacement;

    public ReplaceWithFunctionCallFix(String replacement, String displayReplacement) {
      myReplacement = replacement;
      myDisplayReplacement = displayReplacement;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.lambda.to.method.call.fix.name", myDisplayReplacement);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.lambda.to.method.call.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if (!(element instanceof PsiLambdaExpression)) return;
      PsiElement result = new CommentTracker().replaceAndRestoreComments(element, myReplacement);
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }
  }
}
