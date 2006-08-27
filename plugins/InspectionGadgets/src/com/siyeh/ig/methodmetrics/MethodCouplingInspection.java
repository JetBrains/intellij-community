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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiMethod;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class MethodCouplingInspection extends MethodMetricInspection {

    /**
     * @noinspection PublicField
     */
    public boolean m_includeJavaClasses = false;
    /**
     * @noinspection PublicField
     */
    public boolean m_includeLibraryClasses = false;

    public String getID() {
        return "OverlyCoupledMethod";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final Integer coupling = (Integer)infos[0];
        return InspectionGadgetsBundle.message(
                "method.coupling.problem.descriptor", coupling);
    }

    protected int getDefaultLimit() {
        return 10;
    }

    protected String getConfigurationLabel() {
        return InspectionGadgetsBundle.message("method.coupling.limit.option");
    }

    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final String configurationLabel = getConfigurationLabel();
        final JLabel label = new JLabel(configurationLabel);

        final JFormattedTextField valueField = prepareNumberEditor("m_limit");

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.gridy = 0;
        constraints.weightx = 1.0;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        panel.add(valueField, constraints);

        final JCheckBox arrayCheckBox = new JCheckBox(
                InspectionGadgetsBundle.message(
                        "include.java.system.classes.option"),
                m_includeJavaClasses);
        final ButtonModel arrayModel = arrayCheckBox.getModel();
        arrayModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_includeJavaClasses = arrayModel.isSelected();
            }
        });
        final JCheckBox objectCheckBox = new JCheckBox(
                InspectionGadgetsBundle.message(
                        "include.library.classes.option"),
                m_includeLibraryClasses);
        final ButtonModel model = objectCheckBox.getModel();
        model.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_includeLibraryClasses = model.isSelected();
            }
        });
        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(arrayCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        panel.add(objectCheckBox, constraints);
        return panel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodCouplingVisitor();
    }

    private class MethodCouplingVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            if (method.getNameIdentifier() == null) {
                return;
            }
            final CouplingVisitor visitor = new CouplingVisitor(
                    method, m_includeJavaClasses, m_includeLibraryClasses);
            method.accept(visitor);
            final int coupling = visitor.getNumDependencies();

            if (coupling <= getLimit()) {
                return;
            }
            registerMethodError(method, Integer.valueOf(coupling));
        }
    }
}