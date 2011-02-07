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

import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jdom.Element;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class SerializableInspection extends BaseInspection {

    /** @noinspection PublicField */
    public String superClassString = "java.awt.Component";
    protected List<String> superClassList = new ArrayList();

    protected SerializableInspection() {
        parseString(superClassString, superClassList);
    }

    @Override
    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
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

    private class Form {

        private JPanel contentPanel;
        private ListTable table;
        private JButton addButton;
        private JButton removeButton;

        Form() {
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
        }

        private void createUIComponents() {
            table = new ListTable(new ListWrappingTableModel(superClassList,
                    InspectionGadgetsBundle.message(
                            "ignore.classes.in.hierarchy.column.name")));
        }

        public JPanel getContentPanel() {
            return contentPanel;
        }
    }
}