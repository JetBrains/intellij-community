/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.refactoring;

import org.intellij.lang.xpath.psi.XPathExpression;
import org.intellij.lang.xpath.xslt.util.NameValidator;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.TextFieldWithHistory;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.text.MessageFormat;

public abstract class BaseIntroduceDialog extends DialogWrapper implements RefactoringOptions {
    protected final InputValidator myInputValidator;

    public BaseIntroduceDialog(Project project, NamesValidator validator) {
        super(project, false);
        myInputValidator = new NameValidator(project, validator);
    }

    public boolean isCanceled() {
        return !isOK();
    }

    protected void doOKAction() {
        if (myInputValidator.canClose(getName())) super.doOKAction();
    }

    protected void init(XPathExpression expression, int numberOfExpressions, String title) {
        setModal(true);
        setTitle(title);

        final JLabel jLabel = getTypeLabel();
        jLabel.setText(expression.getType().getName());

        final JCheckBox jCheckBox = getReplaceAll();
        if (numberOfExpressions > 1) {
            jCheckBox.setText(MessageFormat.format(jCheckBox.getText(), String.valueOf(numberOfExpressions)));
        } else {
            jCheckBox.setVisible(false);
        }

        getNameField().addDocumentListener(new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                getOKAction().setEnabled(myInputValidator.checkInput(getName()));
            }
        });

        getOKAction().setEnabled(false);

        init();
    }

    private JCheckBox getReplaceAll() {
        return getForm().myReplaceAll;
    }

    private JLabel getTypeLabel() {
        return getForm().myTypeLabel;
    }

    private TextFieldWithHistory getNameField() {
        return getForm().myNameField;
    }

    public JComponent getPreferredFocusedComponent() {
        return getForm().myNameField;
    }

    public String getName() {
        return getNameField().getText();
    }                                                                             

    protected abstract BaseIntroduceForm getForm();
}
