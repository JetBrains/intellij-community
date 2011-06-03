/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classmetrics;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.psi.PropertyUtils;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MethodCountInspection extends BaseInspection {

    private static final int DEFAULT_METHOD_COUNT_LIMIT = 20;

    @SuppressWarnings({"PublicField"})
    public int m_limit = DEFAULT_METHOD_COUNT_LIMIT;

    @SuppressWarnings({"PublicField"})
    public boolean ignoreGettersAndSetters = false;

    @Override
    @NotNull
    public String getID(){
        return "ClassWithTooManyMethods";
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("too.many.methods.display.name");
    }

    @Override
    public JComponent createOptionsPanel() {
        final JComponent panel = new JPanel(new GridBagLayout());
        final Component label = new JLabel(
                InspectionGadgetsBundle.message("method.count.limit.option"));
        final JFormattedTextField valueField = prepareNumberEditor("m_limit");

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.right = 4;
        constraints.anchor = GridBagConstraints.WEST;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.insets.left = 0;
        constraints.insets.right = 0;
        panel.add(valueField, constraints);

        final CheckBox gettersSettersCheckBox = new CheckBox(
                InspectionGadgetsBundle.message(
                        "method.count.ignore.getters.setters.option"),
                this, "ignoreGettersAndSetters");

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weighty = 1.0;
        constraints.gridwidth = 2;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        panel.add(gettersSettersCheckBox, constraints);

        return panel;
    }


    @Override
    @NotNull
    public String buildErrorString(Object... infos) {
        final Integer count = (Integer)infos[0];
        return InspectionGadgetsBundle.message(
                "too.many.methods.problem.descriptor", count);
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MethodCountVisitor();
    }

    private class MethodCountVisitor extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // note: no call to super
            final int methodCount = calculateTotalMethodCount(aClass);
            if (methodCount <= m_limit) {
                return;
            }
            registerClassError(aClass, Integer.valueOf(methodCount));
        }

        private int calculateTotalMethodCount(PsiClass aClass) {
            final PsiMethod[] methods = aClass.getMethods();
            int totalCount = 0;
            for(final PsiMethod method : methods){
                if (method.isConstructor()) {
                    continue;
                }
                if (ignoreGettersAndSetters) {
                    if (PropertyUtils.isSimpleGetter(method) ||
                            PropertyUtils.isSimpleSetter(method)) {
                        continue;
                    }
                }
                totalCount++;
            }
            return totalCount;
        }
    }
}