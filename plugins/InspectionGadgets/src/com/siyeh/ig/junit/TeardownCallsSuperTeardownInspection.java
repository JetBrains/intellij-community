package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class TeardownCallsSuperTeardownInspection extends MethodInspection {

    private AddSuperTearDownCall fix = new AddSuperTearDownCall();

    public String getDisplayName() {
        return "tearDown() doesn't call super.tearDown()";
    }

    public String getGroupDisplayName() {
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref() doesn't call super.tearDown()";
    }

    private static class AddSuperTearDownCall extends InspectionGadgetsFix{
        public String getName(){
            return "add call to super.tearDown()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            try{
                final PsiElement methodName = descriptor.getPsiElement();
                final PsiMethod method = (PsiMethod) methodName.getParent();
                final PsiCodeBlock body = method.getBody();
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory factory =
                        psiManager.getElementFactory();
                final PsiStatement newStatement =
                        factory.createStatementFromText("super.tearDown();", null);
                final CodeStyleManager styleManager =
                        psiManager.getCodeStyleManager();
                final PsiJavaToken brace = body.getRBrace();
                body.addBefore(newStatement, brace);
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
        return new TeardownCallsSuperTeardownVisitor(this, inspectionManager, onTheFly);
    }

    private static class TeardownCallsSuperTeardownVisitor extends BaseInspectionVisitor {
        private TeardownCallsSuperTeardownVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!"tearDown".equals(methodName)) {
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
            final CallToSuperTeardownVisitor visitor = new CallToSuperTeardownVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperTeardownFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
