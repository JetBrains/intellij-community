package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.*;

public class UnusedLabelInspection extends StatementInspection {
    private final UnusedLabelFix fix = new UnusedLabelFix();

    public String getDisplayName() {
        return "Unused label";
    }

    public String getGroupDisplayName() {
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Unused label #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new UnusedLabelVisitor(this, inspectionManager, onTheFly);
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class UnusedLabelFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove unused label";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            final PsiElement label = descriptor.getPsiElement();
            final PsiLabeledStatement statement = (PsiLabeledStatement) label.getParent();
            final PsiStatement labeledStatement = statement.getStatement();
            final String statementText = labeledStatement.getText();
            replaceStatement(project, statement, statementText);
        }
    }

    private static class UnusedLabelVisitor extends BaseInspectionVisitor {
        private UnusedLabelVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLabeledStatement(PsiLabeledStatement statement) {
            if (containsBreakOrContinueForLabel(statement)) {
                return;
            }
            final PsiIdentifier labelIdentifier = statement.getLabelIdentifier();
            registerError(labelIdentifier);
        }

        private static boolean containsBreakOrContinueForLabel(PsiLabeledStatement statement) {
            final LabelFinder labelFinder = new LabelFinder(statement);
            statement.accept(labelFinder);
            return labelFinder.jumpFound();
        }
    }

    private static class LabelFinder extends PsiRecursiveElementVisitor {
        private boolean m_found = false;
        private String m_label = null;

        private LabelFinder(PsiLabeledStatement target) {
            super();
            final PsiIdentifier labelIdentifier = target.getLabelIdentifier();
            m_label = labelIdentifier.getText();
        }

        private boolean jumpFound() {
            return m_found;
        }

        public void visitReferenceExpression(PsiReferenceExpression ref) {
            final PsiExpression qualifier = ref.getQualifierExpression();
            if (qualifier != null) {
                qualifier.accept(this);
            }
            final PsiReferenceParameterList typeParameters = ref.getParameterList();
            if (typeParameters != null) {
                typeParameters.accept(this);
            }
        }

        public void visitContinueStatement(PsiContinueStatement continueStatement) {
            super.visitContinueStatement(continueStatement);

            final PsiIdentifier labelIdentifier = continueStatement.getLabelIdentifier();
            if (labelMatches(labelIdentifier)) {
                m_found = true;
            }
        }

        public void visitBreakStatement(PsiBreakStatement breakStatement) {
            super.visitBreakStatement(breakStatement);

            final PsiIdentifier labelIdentifier = breakStatement.getLabelIdentifier();

            if (labelMatches(labelIdentifier)) {
                m_found = true;
            }
        }

        private boolean labelMatches(PsiIdentifier labelIdentifier) {
            if (labelIdentifier == null) {
                return false;
            }
            final String labelText = labelIdentifier.getText();
            return labelText.equals(m_label);
        }

    }
}
