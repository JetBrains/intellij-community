package ru.adelf.idea.dotenv.util;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;

import java.util.Arrays;

public class PsiUtil {

    public static boolean isEnvFunctionCall(PsiElement psiElement) {
        return isFunctionReference(psiElement, 0, "getenv", "env");
    }

    public static boolean isFunctionReference(PsiElement psiElement, int parameterIndex, String... funcName) {
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

                return (name != null && Arrays.asList(funcName).contains(name) && getParameterIndex(parameterList, psiElement) == parameterIndex);
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
