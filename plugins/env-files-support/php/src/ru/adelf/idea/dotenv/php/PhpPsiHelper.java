package ru.adelf.idea.dotenv.php;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.*;

import java.util.Arrays;
import java.util.List;

class PhpPsiHelper {

    public static final List<String> FUNCTIONS = Arrays.asList("getenv", "env");

    public static final List<String> ARRAY_NAMES = Arrays.asList("_ENV", "_SERVER");

    /**
     * Checks that this element environment string
     *
     * @param literal Checking psi element
     */
    static boolean isEnvStringLiteral(StringLiteralExpression literal) {
        PsiElement parent = literal.getParent();

        if (parent instanceof ParameterList) {
            return isFunctionParameter(literal, 0, FUNCTIONS);
        }

        if (parent instanceof ArrayIndex) {
            PsiElement arrayAccess = parent.getParent();

            if (arrayAccess instanceof ArrayAccessExpression) {
                return isEnvArrayCall((ArrayAccessExpression) arrayAccess);
            }
        }

        return false;
    }

    /**
     * Checks whether this function reference is reference for env functions, like env or getenv
     * @param functionReference Checking reference
     * @return true if condition filled
     */
    static boolean isEnvFunction(FunctionReference functionReference) {
        String name = functionReference.getName();

        return (name != null && FUNCTIONS.contains(name));
    }

    /**
     * Checks whether this array access is environment call
     * @param arrayAccess Checking array
     * @return true if condition filled
     */
    static boolean isEnvArrayCall(ArrayAccessExpression arrayAccess) {
        PhpPsiElement variable = arrayAccess.getValue();

        if (!(variable instanceof Variable)) return false;

        return (variable.getName() != null && ARRAY_NAMES.contains(variable.getName()));
    }

    @SuppressWarnings("SameParameterValue")
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
    }

    private static int getParameterIndex(ParameterList parameterList, PsiElement parameter) {
        PsiElement[] parameters = parameterList.getParameters();
        for(int i = 0; i < parameters.length; i = i + 1) {
            if(parameters[i].equals(parameter)) {
                return i;
            }
        }

        return -1;
    }
}
