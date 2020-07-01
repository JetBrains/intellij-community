package ru.adelf.idea.dotenv.php;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;

import java.util.Arrays;
import java.util.List;

class PhpPsiHelper {

    public static final List<String> FUNCTIONS = Arrays.asList("getenv", "env");

    /**
     * Checks that this element is first parameter of needed functions, like env('element')
     *
     * @param psiElement Checking psi element
     * @return true if it's needed parameter in needed function
     */
    static boolean isEnvFunctionParameter(PsiElement psiElement) {
        return isFunctionParameter(psiElement, 0, FUNCTIONS);
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
