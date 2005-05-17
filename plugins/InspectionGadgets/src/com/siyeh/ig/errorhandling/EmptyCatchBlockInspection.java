package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class EmptyCatchBlockInspection extends StatementInspection {
    /** @noinspection PublicField*/
    public boolean m_includeComments = true;
    /** @noinspection PublicField*/
    public boolean m_ignoreTestCases = true;

    public String getDisplayName() {
        return "Empty 'catch' block";
    }

    public String getGroupDisplayName() {
        return GroupNames.ERRORHANDLING_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public String buildErrorString(PsiElement location) {
        return "Empty #ref block #loc";
    }

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox commentsCheckBox = new JCheckBox("Comments count as content", m_includeComments);
        final ButtonModel commentsModel = commentsCheckBox.getModel();
        commentsModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_includeComments = commentsModel.isSelected();
            }
        });
        final JCheckBox testCaseCheckBox = new JCheckBox("Ignore empty catch blocks in JUnit test cases", m_ignoreTestCases);
        final ButtonModel model = testCaseCheckBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreTestCases = model.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(commentsCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(testCaseCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EmptyCatchBlockVisitor();
    }

    private class EmptyCatchBlockVisitor extends StatementInspectionVisitor {


        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            if (m_ignoreTestCases) {
                final PsiClass aClass =
                        ClassUtils.getContainingClass(statement);
                if (aClass != null &&
                        ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                    return;
                }
            }

            final PsiCatchSection[] catchSections = statement.getCatchSections();
            for(final PsiCatchSection section : catchSections){
                final PsiCodeBlock block = section.getCatchBlock();
                if(block != null && catchBlockIsEmpty(block)){
                    final PsiElement catchToken = section.getFirstChild();
                    registerError(catchToken);
                }
            }
        }

        private boolean catchBlockIsEmpty(PsiCodeBlock block) {
            if (m_includeComments) {
                final PsiElement[] children = block.getChildren();
                for(final PsiElement child : children){
                    if(child instanceof PsiComment ||
                            child instanceof PsiStatement){
                        return false;
                    }
                }
                return true;
            } else {
                final PsiStatement[] statements = block.getStatements();
                return statements == null || statements.length == 0;
            }
        }
    }

}
