package com.siyeh.ig;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.psi.PsiElement;

import java.lang.reflect.Method;

public abstract class BaseInspection extends LocalInspectionTool {
    private final String m_shortName = null;

    public String getShortName() {
        if (m_shortName == null) {
            final Class aClass = getClass();
            final String name = aClass.getName();
            return name.substring(name.lastIndexOf((int) '.') + 1, name.length() - "Inspection".length());
        }
        return m_shortName;
    }

    protected abstract BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly);

    protected String buildErrorString(PsiElement location) {
        return null;
    }

    protected String buildErrorString(Object arg) {
        return null;
    }


    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return false;
    }

    protected boolean buildQuickFixesOnlyForBatchErrors() {
        return false;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return null;
    }

    public boolean hasQuickFix() {
        final Method[] methods = getClass().getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            final String methodName = method.getName();
            if ("buildFix".equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
