/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.inspection;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.generate.tostring.GenerateToStringContext;
import org.jetbrains.generate.tostring.GenerateToStringUtils;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;
import org.jetbrains.generate.tostring.util.StringUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;

/**
 * Intention to check if the current class overwrites the toString() method.
 * <p/>
 * This inspection will use filter information from the settings to exclude certain fields (eg. constants etc.).
 * <p/>
 * This inspection will only perform inspection if the class have fields to be dumped but
 * does not have a toString method.
 */
public class ClassHasNoToStringMethodInspection extends AbstractToStringInspection {

    private final AbstractGenerateToStringQuickFix fix = new GenerateToStringQuickFix();

    /** User options for classes to exclude. Must be a regexp pattern */
    public String excludeClassNames = "";  // must be public for JDOMSerialization
    /** User options for excluded exception classes */
    public boolean excludeException = true; // must be public for JDOMSerialization
    /** User options for excluded deprecated classes */
    public boolean excludeDeprecated = true; // must be public for JDOMSerialization
    /** User options for excluded enum classes */
    public boolean excludeEnum = false; // must be public for JDOMSerialization
    /** User options for excluded abstract classes */
    public boolean excludeAbstract = false; // must be public for JDOMSerialization

    @NotNull
    public String getDisplayName() {
        return "Class does not overwrite toString() method";
    }

    @NotNull
    public String getShortName() {
        return "ClassHasNoToStringMethod";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            public void visitReferenceExpression(PsiReferenceExpression expression) {
            }

            @Override
            public void visitClass(PsiClass clazz) {
                if (log.isDebugEnabled()) log.debug("checkClass: clazz=" + clazz);

                // must be a class
                if (clazz == null || clazz.getName() == null)
                    return;

                PsiAdapter psi = PsiAdapterFactory.getPsiAdapter();

                // must not be an exception
                if (excludeException && PsiAdapter.isExceptionClass(clazz)) {
                    log.debug("This class is an exception");
                    return;
                }

                // must not be deprecated
              if (excludeDeprecated && clazz.isDeprecated()) {
                    log.debug("Class is deprecated");
                    return;
                }

                // must not be enum
                if (excludeEnum && clazz.isEnum()) {
                    log.debug("Class is an enum");
                    return;
                }

                if (excludeAbstract && psi.isAbstractClass(clazz)) {
                    log.debug("Class is abstract");
                    return;
                }

                // if it is an excluded class - then skip
                if (StringUtil.isNotEmpty(excludeClassNames)) {
                    String name = clazz.getName();
                    if (name != null && name.matches(excludeClassNames)) {
                        log.debug("This class is excluded");
                        return;
                    }
                }

                // must have fields
              PsiField[] fields = clazz.getFields();
                if (fields.length == 0) {
                    log.debug("Class does not have any fields");
                    return;
                }

                // get list of fields and getter methods supposed to be dumped in the toString method
                Project project = clazz.getProject();
                fields = GenerateToStringUtils.filterAvailableFields(project, psi, clazz, GenerateToStringContext.getConfig().getFilterPattern());
                PsiMethod[] methods = null;
                if (GenerateToStringContext.getConfig().isEnableMethods()) {
                    // okay 'getters in code generation' is enabled so check
                    methods = GenerateToStringUtils.filterAvailableMethods(psi, clazz, GenerateToStringContext.getConfig().getFilterPattern());
                }

                // there should be any fields
                if (fields == null && methods == null)
                    return;
                else if (Math.max( fields == null ? 0 : fields.length, methods == null ? 0 : methods.length) == 0)
                    return;

                // okay some fields/getter methods are supposed to dumped, does a toString method exist
                PsiMethod toStringMethod = psi.findMethodByName(clazz, "toString");
                if (toStringMethod == null) {
                    // a toString() method is missing
                    if (log.isDebugEnabled()) log.debug("Class does not overwrite toString() method: " + clazz.getQualifiedName());
                  PsiIdentifier element = clazz.getNameIdentifier();
                  if (element != null) {
                    holder.registerProblem(element, "Class '" + clazz.getName() + "' does not overwrite toString() method", ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
                  }
                }
            }
        };
    }

    /**
     * Creates the options panel in the settings for user changeable options.
     *
     * @return the options panel
     */
    public JComponent createOptionsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Exclude classes (reg exp):"), constraints);

        final JTextField excludeClassNamesField = new JTextField(excludeClassNames, 40);
        excludeClassNamesField.setMinimumSize(new Dimension(140, 20));
        Document document = excludeClassNamesField.getDocument();
        document.addDocumentListener(new DocumentListener() {
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
                excludeClassNames = excludeClassNamesField.getText();
            }
        });
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(excludeClassNamesField, constraints);

        final JCheckBox excludeExceptionCheckBox = new JCheckBox("Exclude exception classes", excludeException);
        final ButtonModel bmException = excludeExceptionCheckBox.getModel();
        bmException.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                excludeException = bmException.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(excludeExceptionCheckBox, constraints);

        final JCheckBox excludeDeprectedCheckBox = new JCheckBox("Exclude deprecated classes", excludeDeprecated);
        final ButtonModel bmDeprecated = excludeDeprectedCheckBox.getModel();
        bmDeprecated.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                excludeDeprecated = bmDeprecated.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(excludeDeprectedCheckBox, constraints);

        final JCheckBox excludeEnumCheckBox = new JCheckBox("Exclude enum classes", excludeEnum);
        final ButtonModel bmEnum = excludeEnumCheckBox.getModel();
        bmEnum.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                excludeEnum = bmEnum.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(excludeEnumCheckBox, constraints);

        final JCheckBox excludeAbstractCheckBox = new JCheckBox("Exclude abstract classes", excludeAbstract);
        final ButtonModel bmAbstract = excludeAbstractCheckBox.getModel();
        bmAbstract.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                excludeAbstract = bmAbstract.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(excludeAbstractCheckBox, constraints);

        return panel;
    }


}
