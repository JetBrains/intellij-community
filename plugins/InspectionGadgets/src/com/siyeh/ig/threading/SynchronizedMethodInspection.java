package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class SynchronizedMethodInspection extends MethodInspection {
    public boolean m_includeNativeMethods = true;
    private SynchronizedMethodFix fix = new SynchronizedMethodFix();

    public String getDisplayName() {
        return "'synchronized' method";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifierList = (PsiModifierList) location.getParent();
        final PsiMethod method = (PsiMethod) modifierList.getParent();
        return "Method " + method.getName() + "() declared '#ref' #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        final PsiMethod method = (PsiMethod) location.getParent().getParent();
        if(method.getBody()== null)
        {
            return null;
        }

        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new SynchronizedMethodVisitor(this, inspectionManager, onTheFly);
    }

    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Include native methods",
                this, "m_includeNativeMethods");
    }

    public static class SynchronizedMethodFix extends InspectionGadgetsFix{
        public String getName(){
            return "Move synchronization into method";
        }

        public void applyFix(Project project,
                             ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            try{
                final PsiElement nameElement =
                        descriptor.getPsiElement();
                final PsiMethod method =
                        (PsiMethod) nameElement.getParent().getParent();
                method.getModifierList()
                        .setModifierProperty(PsiModifier.SYNCHRONIZED, false);
                final PsiCodeBlock body = method.getBody();
                final String text = body.getText();
                final String replacementText;
                if(method.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiClass containingClass = method.getContainingClass();
                    final String className = containingClass.getName();
                    replacementText = "{ synchronized(" + className + ".class){" +
                            text.substring(1) + '}';
                } else{
                    replacementText = "{ synchronized(this){" + text.substring(1) + '}';
                }
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory elementFactory =
                        psiManager.getElementFactory();
                final PsiCodeBlock block =
                        elementFactory.createCodeBlockFromText(replacementText,
                                                               null);
                body.replace(block);
                psiManager.getCodeStyleManager().reformat(method);
            } catch(IncorrectOperationException e){
            }
        }
    }

    private class SynchronizedMethodVisitor extends BaseInspectionVisitor {
        private SynchronizedMethodVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method) {
            //no call to super, so we don't drill into anonymous classes
            if (!method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) {
                return;
            }
            if (!m_includeNativeMethods &&
                    method.hasModifierProperty(PsiModifier.NATIVE)) {
                return;
            }
            registerModifierError(PsiModifier.SYNCHRONIZED, method);
        }

    }

}
