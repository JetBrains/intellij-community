package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.codeInspection.reference.RefMethod;

import java.util.Set;
import java.util.HashSet;
import java.util.Stack;
import java.util.Collection;


public class MethodInheritanceUtils {
    private MethodInheritanceUtils() {
        super();
    }

    public static Set<PsiMethod> calculateSiblingMethods(PsiMethod method) {
        final Set<PsiMethod> siblingMethods = new HashSet<PsiMethod>();
        final Stack<PsiMethod> pendingMethods = new Stack<PsiMethod>();
        pendingMethods.add(method);
        while (!pendingMethods.isEmpty()) {
            final PsiMethod methodToAnalyze = pendingMethods.pop();
            siblingMethods.add(methodToAnalyze);
            final SearchScope scope = methodToAnalyze.getUseScope();
            final Collection<PsiMethod> overridingMethods =
                    OverridingMethodsSearch.search(methodToAnalyze, scope, true).findAll();
            for (PsiMethod overridingMethod : overridingMethods) {
                if (!siblingMethods.contains(overridingMethod) &&
                        !pendingMethods.contains(overridingMethod)) {
                    pendingMethods.add(overridingMethod);
                }
            }
            final PsiMethod[] superMethods = methodToAnalyze.findSuperMethods();
            for (PsiMethod superMethod : superMethods) {
                if (!siblingMethods.contains(superMethod) &&
                        !pendingMethods.contains(superMethod)) {
                    pendingMethods.add(superMethod);
                }
            }
        }
        return siblingMethods;
    }

    public static boolean hasSiblingMethods(PsiMethod method) {
        final SearchScope scope = method.getUseScope();
        final Collection<PsiMethod> overridingMethods =
                OverridingMethodsSearch.search(method, scope, true).findAll();
        if (overridingMethods.size() != 0) {
            return true;
        }
        final PsiMethod[] superMethods = method.findSuperMethods();
        return superMethods.length != 0;

    }

    public static boolean inheritsFromLibraryMethod(PsiMethod method) {
        final Set<PsiMethod> superMethods = calculateSiblingMethods(method);
        for (PsiMethod superMethod : superMethods) {
            if(LibraryUtil.classIsInLibrary(superMethod.getContainingClass()))
            {
                return true;
            }
        }
        return false;
    }

    public static Set<RefMethod> calculateSiblingMethods(RefMethod method) {
        final Set<RefMethod> siblingMethods = new HashSet<RefMethod>();
        final Stack<RefMethod> pendingMethods = new Stack<RefMethod>();
        pendingMethods.add(method);
        while (!pendingMethods.isEmpty()) {
            final RefMethod methodToAnalyze = pendingMethods.pop();
            siblingMethods.add(methodToAnalyze);
            Collection<RefMethod> overridingMethods = methodToAnalyze.getDerivedMethods();
            for (RefMethod overridingMethod : overridingMethods) {
                if (!siblingMethods.contains(overridingMethod) &&
                        !pendingMethods.contains(overridingMethod)) {
                    pendingMethods.add(overridingMethod);
                }
            }
            final Collection<RefMethod> superMethods = methodToAnalyze.getSuperMethods();
            for (RefMethod superMethod : superMethods) {
                if (!siblingMethods.contains(superMethod) &&
                        !pendingMethods.contains(superMethod)) {
                    pendingMethods.add(superMethod);
                }
            }
        }
        return siblingMethods;
    }
}