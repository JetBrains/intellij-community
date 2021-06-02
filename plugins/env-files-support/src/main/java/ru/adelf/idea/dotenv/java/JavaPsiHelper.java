package ru.adelf.idea.dotenv.java;

import com.intellij.psi.*;
import com.jetbrains.php.lang.psi.elements.ArrayAccessExpression;
import com.jetbrains.php.lang.psi.elements.PhpPsiElement;
import com.jetbrains.php.lang.psi.elements.Variable;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class JavaPsiHelper {
    public static final List<String> ARRAY_NAMES = Arrays.asList("_ENV", "_SERVER");

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

        if (isDirectMethodCall(methodName)) {
            return true;
        }

        List<String> classNames = getClassNames(methodName);

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

    private static boolean isDirectMethodCall(String methodName) {
        return methodName.equals("getenv") || methodName.equals("getEnv");
    }

    @Nullable
    private static List<String> getClassNames(String methodName) {
        switch (methodName) {
            case "get":
                return Arrays.asList("Dotenv", "DotEnv");
            case "getProperty":
                return Collections.singletonList("System");
        }

        return null;
    }

    /**
     * Checks whether this array access is environment call
     *
     * @param arrayAccess Checking array
     * @return true if condition filled
     */
    static boolean isEnvArrayCall(ArrayAccessExpression arrayAccess) {
        PhpPsiElement variable = arrayAccess.getValue();

        if (!(variable instanceof Variable)) return false;

        return (variable.getName() != null && ARRAY_NAMES.contains(variable.getName()));
    }

/*    @SuppressWarnings("SameParameterValue")
    private static boolean isFunctionParameter(PsiElement psiElement, int parameterIndex, List<String> functions) {
        PsiElement variableContext = psiElement.getContext();
        if(!(variableContext instanceof ParameterList)) {
            return false;
        } else {
            ParameterList parameterList = (ParameterList) variableContext;
            PsiElement context = parameterList.getContext();
            if(!(context instanceof FunctionReference)) {
                return false;
            } else {
                FunctionReference methodReference = (FunctionReference) context;
                String name = methodReference.getName();

                return (name != null && functions.contains(name) && getParameterIndex(parameterList, psiElement) == parameterIndex);
            }
        }
    }*/

/*    private static int getParameterIndex(ParameterList parameterList, PsiElement parameter) {
        PsiElement[] parameters = parameterList.getParameters();
        for(int i = 0; i < parameters.length; i = i + 1) {
            if(parameters[i].equals(parameter)) {
                return i;
            }
        }

        return -1;
    }*/
}
