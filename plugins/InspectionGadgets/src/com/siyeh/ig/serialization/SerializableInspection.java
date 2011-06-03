/*
 * Copyright 2007-2011 Bas Leijdekkers
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
package com.siyeh.ig.serialization;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.CheckBox;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreAnonymousInnerClasses = false;
    @SuppressWarnings({"PublicField"})
    public String superClassString = "java.awt.Component";
    protected List<String> superClassList = new ArrayList();

    protected SerializableInspection() {
        parseString(superClassString, superClassList);
    }

    @Override
    public JComponent createOptionsPanel() {
        final JComponent panel = new JPanel(new GridBagLayout());

        final ListTable table = new ListTable(new ListWrappingTableModel(
                superClassList, InspectionGadgetsBundle.message(
                "ignore.classes.in.hierarchy.column.name")));
        final JScrollPane scrollPane =
                ScrollPaneFactory.createScrollPane(table);
        final ActionToolbar toolbar =
                UiUtils.createAddRemoveTreeAnnotationChooserToolbar(table,
                        InspectionGadgetsBundle.message(
                                "choose.super.class.to.ignore"));
        final CheckBox checkBox = new CheckBox(InspectionGadgetsBundle.message(
                "ignore.anonymous.inner.classes"), this,
                "ignoreAnonymousInnerClasses");

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.right = 4;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(toolbar.getComponent(), constraints);

        constraints.gridy = 1;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, constraints);

        constraints.gridy = 2;
        constraints.weighty = 0.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(checkBox, constraints);

        return panel;
    }

    @Override
    public void readSettings(Element node) throws InvalidDataException {
        super.readSettings(node);
        parseString(superClassString, superClassList);
    }

    @Override
    public void writeSettings(Element node) throws WriteExternalException {
        superClassString = formatString(superClassList);
        super.writeSettings(node);
    }

    protected boolean isIgnoredSubclass(PsiClass aClass) {
        if (SerializationUtils.isDirectlySerializable(aClass)) {
            return false;
        }
        for (String superClassName : superClassList) {
            if (InheritanceUtil.isInheritor(aClass, superClassName)) {
                return true;
            }
        }
        return false;
    }
}