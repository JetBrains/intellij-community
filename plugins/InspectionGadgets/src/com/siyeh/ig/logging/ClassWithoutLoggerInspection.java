/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.logging;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.RegExInputVerifier;
import org.jetbrains.annotations.NotNull;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;

public class ClassWithoutLoggerInspection extends ClassInspection {

    /** @noinspection PublicField*/
    public String loggerClassName = "java.util.logging.Logger";

    /** @noinspection PublicField*/
    public boolean ignoreSuperLoggers = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("no.logger.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.LOGGING_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message("no.logger.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassWithoutLoggerVisitor();
    }

    private class ClassWithoutLoggerVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            //no recursion to avoid drilldown
            if (aClass.isInterface() || aClass.isEnum()||
                    aClass.isAnnotationType()){
                return;
            }
            if (aClass instanceof PsiTypeParameter ||
                    aClass instanceof PsiAnonymousClass) {
                return;
            }
            if (aClass.getContainingClass()!=null) {
                return;
            }
            final PsiField[] fields;
            if (ignoreSuperLoggers) {
                fields = aClass.getAllFields();
            } else {
                fields = aClass.getFields();
            }
            for (PsiField field : fields) {
                if (isLogger(field)) {
                    if(PsiUtil.isAccessible(field, aClass, aClass)) {
                        return;
                    }
                }
            }
            registerClassError(aClass);
        }

        private boolean isLogger(PsiField field) {
            final PsiType type = field.getType();
            final String text = type.getCanonicalText();
            return text.equals(loggerClassName);
        }
    }

    private class Form {

        private JPanel contentPanel;
        private JTextField loggerClassNameTextField;
        private JCheckBox ignoreCheckBox;

        Form() {
            super();
            loggerClassNameTextField.setText(loggerClassName);
            loggerClassNameTextField.setInputVerifier(new RegExInputVerifier());
            final DocumentListener listener = new DocumentListener() {

                public void changedUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void insertUpdate(DocumentEvent e) {
                    textChanged();
                }

                public void removeUpdate(DocumentEvent e) {
                    textChanged();
                }

                private void textChanged() {
                    loggerClassName =  loggerClassNameTextField.getText();
                }
            };
            final Document document = loggerClassNameTextField.getDocument();
            document.addDocumentListener(listener);
            ignoreCheckBox.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    ignoreSuperLoggers = ignoreCheckBox.isSelected();
                }
            });
        }

        public JPanel getContentPanel() {
            return contentPanel;
        }
    }
}