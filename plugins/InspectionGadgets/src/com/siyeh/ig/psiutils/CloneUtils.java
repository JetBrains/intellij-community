package com.siyeh.ig.psiutils;

import com.intellij.psi.*;

import java.util.HashSet;
import java.util.Set;

public class CloneUtils {
    private CloneUtils() {
        super();

    }

    public static boolean isCloneable(PsiClass aClass) {
        return isCloneable(aClass, new HashSet());
    }

    private static boolean isCloneable(PsiClass aClass, Set alreadyChecked) {
        final String qualifiedName = aClass.getQualifiedName();
        if (alreadyChecked.contains(qualifiedName)) {
            return false;
        }
        alreadyChecked.add(qualifiedName);
        final PsiClass[] supers = aClass.getSupers();
        for (int i = 0; i < supers.length; i++) {
            final PsiClass aSuper = supers[i];
            if ("java.lang.Cloneable".equals(aSuper.getQualifiedName())) {
                return true;
            }
            if (isCloneable(aSuper, alreadyChecked)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDirectlyCloneable(PsiClass aClass) {
        final PsiClass[] interfaces = aClass.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            final String qualifiedName = interfaces[i].getQualifiedName();
            if ("java.lang.Cloneable".equals(qualifiedName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isClone(PsiMethod method) {
        final String methodName = method.getName();
        if (!"clone".equals(methodName)) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 0) {
            return false;
        }
        final PsiType returnType = method.getReturnType();
        if (!TypeUtils.isJavaLangObject(returnType)) {
            return false;
        }
        return true;
    }

}
