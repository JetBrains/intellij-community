package com.siyeh.ig;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;

import java.lang.reflect.Method;

public abstract class BaseInspection extends LocalInspectionTool {
    private final String m_shortName = null;
    private InspectionRunListener listener = null;

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

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return null;
    }

    public ProblemDescriptor[] checkMethod(PsiMethod method,
                                           InspectionManager manager,
                                           boolean isOnTheFly){
        bindListener();
        final long start = System.currentTimeMillis();
        try{
            return doCheckMethod(method, manager, isOnTheFly);
        } finally{
            final long end = System.currentTimeMillis();
            listener.reportRun(getDisplayName(), end-start);
        }
    }

    protected ProblemDescriptor[] doCheckMethod(PsiMethod method,
                                              InspectionManager manager,
                                              boolean isOnTheFly){
        return super.checkMethod(method, manager, isOnTheFly);
    }

    public ProblemDescriptor[] checkClass(PsiClass aClass,
                                          InspectionManager manager,
                                          boolean isOnTheFly){
        bindListener();
        final long start = System.currentTimeMillis();
        try{
            return doCheckClass(aClass, manager, isOnTheFly);
        } finally{
            final long end = System.currentTimeMillis();
            listener.reportRun(getDisplayName(), end - start);
        }
    }

    protected ProblemDescriptor[] doCheckClass(PsiClass aClass,
                                              InspectionManager manager,
                                              boolean isOnTheFly){
        return super.checkClass(aClass, manager, isOnTheFly);
    }

    public ProblemDescriptor[] checkField(PsiField field,
                                          InspectionManager manager,
                                          boolean isOnTheFly){
        bindListener();
        final long start = System.currentTimeMillis();
        try{
            return doCheckField(field, manager, isOnTheFly);
        } finally{
            final long end = System.currentTimeMillis();
            listener.reportRun(getDisplayName(), end - start);
        }
    }

    private void bindListener(){
        if(listener== null)
        {
            final Application application = ApplicationManager.getApplication();
            final InspectionGadgetsPlugin plugin =
                    (InspectionGadgetsPlugin) application.getComponent("InspectionGadgets");
            listener = plugin.getTelemetry();
        }
    }

    protected ProblemDescriptor[] doCheckField(PsiField field,
                                             InspectionManager manager,
                                             boolean isOnTheFly){
        return super.checkField(field, manager, isOnTheFly);
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
