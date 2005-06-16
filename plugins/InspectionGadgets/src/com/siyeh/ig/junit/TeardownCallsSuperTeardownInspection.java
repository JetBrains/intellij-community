package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

public class TeardownCallsSuperTeardownInspection extends MethodInspection{
    private final AddSuperTearDownCall fix = new AddSuperTearDownCall();

    public String getID(){
        return "TearDownDoesntCallSuperTearDown";
    }

    public String getDisplayName(){
        return "'tearDown()' doesn't call 'super.tearDown()'";
    }

    public String getGroupDisplayName(){
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() doesn't call super.tearDown()";
    }

    private static class AddSuperTearDownCall extends InspectionGadgetsFix{
        public String getName(){
            return "add call to super.tearDown()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement methodName = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod) methodName.getParent();
            assert method != null;
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
        }
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new TeardownCallsSuperTeardownVisitor();
    }

    private static class TeardownCallsSuperTeardownVisitor
            extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!"tearDown".equals(methodName)){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.ABSTRACT)){
                return;
            }
            if(method.getBody() == null){
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            if(parameterList.getParameters().length != 0){
                return;
            }

            final PsiClass targetClass = method.getContainingClass();
            if(targetClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(targetClass, "junit.framework.TestCase")){
                return;
            }
            final CallToSuperTeardownVisitor visitor = new CallToSuperTeardownVisitor();
            method.accept(visitor);
            if(visitor.isCallToSuperTeardownFound()){
                return;
            }
            registerMethodError(method);
        }
    }
}
