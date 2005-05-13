package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class ReturnNullInspection extends StatementInspection {
    /** @noinspection PublicField*/
    public boolean m_reportObjectMethods = true;
    /** @noinspection PublicField*/
    public boolean m_reportArrayMethods = true;

    public String getID(){
        return "ReturnOfNull";
    }
    public String getDisplayName() {
        return "Return of 'null'";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Return of '#ref' #loc";
    }

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox arrayCheckBox = new JCheckBox("Methods that return arrays", m_reportArrayMethods);
        final ButtonModel arrayModel = arrayCheckBox.getModel();
        arrayModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_reportArrayMethods = arrayModel.isSelected();
            }
        });
        final JCheckBox objectCheckBox = new JCheckBox("Methods that return objects", m_reportObjectMethods);
        final ButtonModel model = objectCheckBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_reportObjectMethods = model.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(arrayCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(objectCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ReturnNullVisitor(this, inspectionManager, onTheFly);
    }

    private class ReturnNullVisitor extends StatementInspectionVisitor {
        private ReturnNullVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitLiteralExpression(@NotNull PsiLiteralExpression value) {
            super.visitLiteralExpression(value);
            final String text = value.getText();
            if (!"null".equals(text)) {
                return;
            }
            PsiElement parent = value.getParent();
            while (parent != null &&
                    (parent instanceof PsiParenthesizedExpression ||
                    parent instanceof PsiConditionalExpression ||
                    parent instanceof PsiTypeCastExpression)) {
                parent = parent.getParent();
            }
            if (parent == null || !(parent instanceof PsiReturnStatement)) {
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(value,
                                                            PsiMethod.class);
            if(method == null) {
                return;
            }
            final PsiType returnType = method.getReturnType();
            if (returnType == null) {
                return;
            }
            final boolean isArray = returnType.getArrayDimensions() > 0;
            if (m_reportArrayMethods && isArray) {
                registerError(value);
            }
            if (m_reportObjectMethods && !isArray) {
                registerError(value);
            }
        }

    }

}
