package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;

public class StringBufferReplaceableByStringBuilderInspection extends ExpressionInspection {
    private final StringBufferMayBeStringBuilderFix fix = new StringBufferMayBeStringBuilderFix();

    public String getDisplayName() {
        return "StringBuffer may be StringBuilder (J2SDK 5.0 only)";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "StringBuffer #ref may be declared as StringBuilder #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class StringBufferMayBeStringBuilderFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Replace with StringBuilder";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            final PsiElement variableIdentifier =
                    descriptor.getPsiElement();
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) variableIdentifier.getParent().getParent();
            final String text = declarationStatement.getText();
            final String newStatement = text.replaceAll("StringBuffer", "StringBuilder");
            replaceStatement(project, declarationStatement, newStatement);
        }
    }
    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new StringBufferReplaceableByStringBuilderVisitor(this, inspectionManager, onTheFly);
    }

    private static class StringBufferReplaceableByStringBuilderVisitor extends BaseInspectionVisitor {
        private StringBufferReplaceableByStringBuilderVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLocalVariable(PsiLocalVariable variable) {
            super.visitLocalVariable(variable);
            final PsiManager manager = variable.getManager();
            final LanguageLevel languageLevel = manager.getEffectiveLanguageLevel();
            if (languageLevel.equals(LanguageLevel.JDK_1_3) ||
                    languageLevel.equals(LanguageLevel.JDK_1_4)) {
                return;
            }
            final PsiCodeBlock codeBlock =
                    (PsiCodeBlock) PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (codeBlock == null) {
                return;
            }
            final PsiType type = variable.getType();
            if (!TypeUtils.typeEquals("java.lang.StringBuffer", type)) {
                return;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (initializer == null) {
                return;
            }
            if (!isNewStringBuffer(initializer)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsAssignedFrom(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsReturned(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, codeBlock)) {
                return;
            }
            if (VariableAccessUtils.variableIsUsedInInnerClass(variable, codeBlock)) {
                return;
            }
            registerVariableError(variable);
        }

        private boolean isNewStringBuffer(PsiExpression expression) {
            if (expression == null) {
                return false;
            } else if (expression instanceof PsiNewExpression) {
                return true;
            } else if (expression instanceof PsiMethodCallExpression) {
                final PsiReferenceExpression methodExpression =
                        ((PsiMethodCallExpression) expression).getMethodExpression();
                final String methodName = methodExpression.getReferenceName();
                if (!"append".equals(methodName)) {
                    return false;
                }
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                return isNewStringBuffer(qualifier);
            }
            return false;
        }
    }

}
