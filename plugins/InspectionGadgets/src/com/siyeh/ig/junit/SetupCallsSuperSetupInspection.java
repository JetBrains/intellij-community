package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class SetupCallsSuperSetupInspection extends MethodInspection {
    private final AddSuperSetUpCall fix = new AddSuperSetUpCall();

    public String getDisplayName() {
        return "setUp() doesn't call super.setUp()";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't call super.setUp()";
    }

    private static class AddSuperSetUpCall extends InspectionGadgetsFix{
        public String getName(){
            return "add call to super.setUp()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            try{
                final PsiElement methodName = descriptor.getPsiElement();
                final PsiMethod method = (PsiMethod) methodName.getParent();
                final PsiCodeBlock body = method.getBody();
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory factory =
                        psiManager.getElementFactory();
                final PsiStatement newStatement =
                        factory.createStatementFromText("super.setUp();", null);
                final CodeStyleManager styleManager =
                        psiManager.getCodeStyleManager();
                final PsiJavaToken brace = body.getLBrace();
                body.addAfter(newStatement, brace);
                styleManager.reformat(body);
            } catch(IncorrectOperationException e){
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SetupCallsSuperSetupVisitor(this, inspectionManager, onTheFly);
    }

    private static class SetupCallsSuperSetupVisitor extends BaseInspectionVisitor {
        private SetupCallsSuperSetupVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"setUp".equals(methodName)) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return;
            }
            if (parameterList.getParameters().length != 0) {
                return;
            }

            final PsiClass targetClass = method.getContainingClass();
            if (!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")) {
                return;
            }
            final CallToSuperSetupVisitor visitor = new CallToSuperSetupVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperSetupFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
