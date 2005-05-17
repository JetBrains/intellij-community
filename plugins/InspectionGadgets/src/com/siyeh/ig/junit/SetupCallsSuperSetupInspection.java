package com.siyeh.ig.junit;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
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

public class SetupCallsSuperSetupInspection extends MethodInspection{
    private final AddSuperSetUpCall fix = new AddSuperSetUpCall();

    public String getID(){
        return "SetUpDoesntCallSuperSetUp";
    }

    public String getDisplayName(){
        return "'setUp()' doesn't call 'super.setUp()'";
    }

    public String getGroupDisplayName(){
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() doesn't call super.setUp()";
    }

    private static class AddSuperSetUpCall extends InspectionGadgetsFix{
        public String getName(){
            return "add call to super.setUp()";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)){
                return;
            }
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

    public BaseInspectionVisitor buildVisitor(){
        return new SetupCallsSuperSetupVisitor();
    }

    private static class SetupCallsSuperSetupVisitor
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!"setUp".equals(methodName)){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.ABSTRACT))
            {
                return;
            }
            if(method.getBody()==null)
            {
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
            final CallToSuperSetupVisitor visitor =
                    new CallToSuperSetupVisitor();
            method.accept(visitor);
            if(visitor.isCallToSuperSetupFound()){
                return;
            }
            registerMethodError(method);
        }
    }
}
