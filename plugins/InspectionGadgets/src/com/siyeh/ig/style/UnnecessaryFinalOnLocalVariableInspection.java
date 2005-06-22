package com.siyeh.ig.style;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.ig.psiutils.VariableUsedInInnerClassVisitor;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryFinalOnLocalVariableInspection extends MethodInspection {
    public String getDisplayName() {
        return "Unnecessary 'final' for local variable";
    }

    public String getGroupDisplayName() {
        return GroupNames.STYLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        final PsiModifierList modifierList = (PsiModifierList) location.getParent();
        assert modifierList != null;
        final PsiVariable parameter = (PsiVariable) modifierList.getParent();
        assert parameter != null;
        final String parameterName = parameter.getName();
        return "Unnecessary #ref for variable " + parameterName + " #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnnecessaryFinalOnLocalVariableVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new RemoveModifierFix(location);
    }

    private static class UnnecessaryFinalOnLocalVariableVisitor extends BaseInspectionVisitor {

        public void visitDeclarationStatement(PsiDeclarationStatement statement) {
            super.visitDeclarationStatement(statement);
            final PsiElement[] declaredElements =
                    statement.getDeclaredElements();
            if (declaredElements == null || declaredElements.length == 0) {
                return;
            }
            for(final PsiElement declaredElement : declaredElements){
                if(!(declaredElement instanceof PsiLocalVariable)){
                    return;
                }
                final PsiLocalVariable variable = (PsiLocalVariable) declaredElement;
                if(!variable.hasModifierProperty(PsiModifier.FINAL)){
                    return;
                }
            }
            final PsiCodeBlock containingBlock =
                    PsiTreeUtil.getParentOfType(statement, PsiCodeBlock.class);
            if (containingBlock == null) {
                return;
            }
            for(PsiElement declaredElement1 : declaredElements){
                final PsiLocalVariable variable = (PsiLocalVariable) declaredElement1;
                if(variableIsUsedInInnerClass(containingBlock, variable)){
                    return;
                }
            }
            final PsiLocalVariable variable1 = (PsiLocalVariable) statement.getDeclaredElements()[0];
            registerModifierError(PsiModifier.FINAL, variable1);
        }

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for(PsiCatchSection catchSection : catchSections){
                final PsiParameter parameter = catchSection.getParameter();
                final PsiCodeBlock catchBlock = catchSection.getCatchBlock();
                if(parameter == null || catchBlock == null){
                    continue;
                }
                if(parameter.hasModifierProperty(PsiModifier.FINAL) &&
                        !variableIsUsedInInnerClass(catchBlock, parameter)){
                    registerModifierError(PsiModifier.FINAL, parameter);
                }
            }
        }


        private static boolean variableIsUsedInInnerClass(PsiCodeBlock block,
                                                          PsiVariable variable) {

            final VariableUsedInInnerClassVisitor visitor
                    = new VariableUsedInInnerClassVisitor(variable);
            block.accept(visitor);
            return visitor.isUsedInInnerClass();
        }

    }

}
