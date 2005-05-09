package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.fixes.NormalizeDeclarationFix;

public class MultipleDeclarationInspection extends VariableInspection {
    private final NormalizeDeclarationFix fix = new NormalizeDeclarationFix();

    public String getID(){
        return "MultipleVariablesInDeclaration";
    }
    public String getDisplayName() {
        return "Multiple variables in one declaration";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Multiple variables in one declaration #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new MultipleDeclarationVisitor(this, inspectionManager, onTheFly);
    }

    private static class MultipleDeclarationVisitor extends BaseInspectionVisitor {
        private MultipleDeclarationVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            if (statement.getDeclaredElements().length <= 1) {
                return;
            }
            final PsiElement parent = statement.getParent();
            if (parent instanceof PsiForStatement) {
                final PsiForStatement forStatement = (PsiForStatement) parent;
                final PsiStatement initialization = forStatement.getInitialization();
                if (statement.equals(initialization)) {
                    return;
                }
            }
            final PsiElement[] declaredVars = statement.getDeclaredElements();
            for (int i = 1; i < declaredVars.length; i++) {  //skip the first one;
                final PsiLocalVariable var = (PsiLocalVariable) declaredVars[i];
                registerVariableError(var);
            }
        }

        public void visitField(PsiField field) {
            super.visitField(field);
            if (childrenContainTypeElement(field)) {
                return;
            }
            if (field instanceof PsiEnumConstant) {
                return;
            }
            registerFieldError(field);
        }

        public static boolean childrenContainTypeElement(PsiElement field) {
            final PsiElement[] children = field.getChildren();
            for(PsiElement aChildren : children){
                if(aChildren instanceof PsiTypeElement){
                    return true;
                }
            }
            return false;
        }


    }
}