package com.siyeh.ig.logging;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.ig.*;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;

public class PublicMethodWithoutLoggingInspection extends MethodInspection{
    /** @noinspection PublicField*/
    public String loggerClassName = "java.util.logging.Logger";

    public String getDisplayName(){
        return "Public method without logging";
    }

    public String getGroupDisplayName(){
        return GroupNames.LOGGING_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);

        final JLabel classNameLabel = new JLabel("Logger class name:");
        classNameLabel.setHorizontalAlignment(SwingConstants.TRAILING);

        final JTextField loggerClassNameField = new JTextField();
        final Font panelFont = panel.getFont();
        loggerClassNameField.setFont(panelFont);
        loggerClassNameField.setText(loggerClassName);
        loggerClassNameField.setColumns(100);
        loggerClassNameField.setInputVerifier(new RegExInputVerifier());

        final DocumentListener listener = new DocumentListener(){
            public void changedUpdate(DocumentEvent e){
                textChanged();
            }

            public void insertUpdate(DocumentEvent e){
                textChanged();
            }

            public void removeUpdate(DocumentEvent e){
                textChanged();
            }

            private void textChanged(){
                loggerClassName = loggerClassNameField.getText();
            }
        };
        final Document loggerClassNameDocument =
                loggerClassNameField.getDocument();
        loggerClassNameDocument.addDocumentListener(listener);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(classNameLabel, constraints);

        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(loggerClassNameField, constraints);

        return panel;
    }

    public String buildErrorString(PsiElement location){
        return "Public method without logging #ref #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new PublicMethodWithoutLoggingVisitor(this, inspectionManager,
                                                     onTheFly);
    }

    private class PublicMethodWithoutLoggingVisitor
            extends BaseInspectionVisitor{
        private PublicMethodWithoutLoggingVisitor(BaseInspection inspection,
                                                  InspectionManager inspectionManager,
                                                  boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(PsiMethod method){
            //no drilldown
            if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            if(method.getBody() == null){
                return;
            }
            if(method.isConstructor()){
                return;
            }

            if(PropertyUtil.isSimplePropertyAccessor(method)){
                return;
            }
            if(containsLoggingCall(method)){
                return;
            }
            registerMethodError(method);
        }

        private boolean containsLoggingCall(PsiMethod method){
            final ContainsLoggingCallVisitor visitor =
                    new ContainsLoggingCallVisitor();
            method.accept(visitor);
            return visitor.containsLoggingCall();
        }
    }

    private class ContainsLoggingCallVisitor
            extends PsiRecursiveElementVisitor{
        private boolean containsLoggingCall = false;

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(containsLoggingCall){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            final String containingClassName = containingClass.getQualifiedName();
            if(containingClassName.equals(loggerClassName)){
                containsLoggingCall = true;
            }
        }

        public boolean containsLoggingCall(){
            return containsLoggingCall;
        }
    }
}
