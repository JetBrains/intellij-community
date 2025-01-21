package ru.adelf.idea.dotenv.java;

import com.intellij.psi.*;

import java.util.List;

class JavaPsiHelper {
    /**
     * Checks that this element environment string
     *
     * @param literal Checking psi element
     */
    static boolean isEnvStringLiteral(PsiLiteralExpression literal) {
        PsiElement parent = literal.getParent();

        if (parent instanceof PsiExpressionList) {
            PsiExpression[] expressions = ((PsiExpressionList) parent).getExpressions();
            if (expressions.length < 1) return false;

            if (expressions[0] != literal) return false;

            PsiElement methodCall = parent.getParent();

            if (!(methodCall instanceof PsiMethodCallExpression)) return false;

            return isEnvMethodCall((PsiMethodCallExpression) methodCall);
        }

        return false;
    }

    /**
     * Checks whether this function reference is reference for env functions, like env or getenv
     *
     * @param methodCallExpression Checking reference
     * @return true if condition filled
     */
    static boolean isEnvMethodCall(PsiMethodCallExpression methodCallExpression) {
        PsiElement nameElement = methodCallExpression.getMethodExpression().getReferenceNameElement();

        if (nameElement == null) {
            return false;
        }

        String methodName = nameElement.getText();

        if (JavaEnvironmentClasses.isDirectMethodCall(methodName)) {
            return true;
        }

        List<String> classNames = JavaEnvironmentClasses.getClassNames(methodName);

        if (classNames == null) {
            return false;
        }

        for (ResolveResult result : methodCallExpression.getMethodExpression().multiResolve(true)) {
            if (result.getElement() instanceof PsiMethod) {
                PsiClass psiClass = ((PsiMethod) result.getElement()).getContainingClass();

                if (psiClass != null && classNames.contains(psiClass.getName())) {
                    return true;
                }
            }
        }

        return false;
    }
}
