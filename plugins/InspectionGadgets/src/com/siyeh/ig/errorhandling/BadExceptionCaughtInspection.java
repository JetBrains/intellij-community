/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.codeInspection.ui.AddAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BadExceptionCaughtInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public String exceptionsString =
            "java.lang.NullPointerException" + ',' +
            "java.lang.IllegalMonitorStateException" + ',' +
            "java.lang.ArrayIndexOutOfBoundsException";

    final List<String> exceptionList = new ArrayList<String>(32);

    public BadExceptionCaughtInspection() {
        parseString(exceptionsString, exceptionList);
    }

    @NotNull
    public String getID() {
        return "ProhibitedExceptionCaught";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "bad.exception.caught.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "bad.exception.caught.problem.descriptor");
    }

    public JComponent createOptionsPanel() {
        final Form form = new Form();
        return form.getContentPanel();
    }

    public void readSettings(Element element) throws InvalidDataException {
        super.readSettings(element);
        parseString(exceptionsString, exceptionList);
    }

    public void writeSettings(Element element) throws WriteExternalException {
        exceptionsString = formatString(exceptionList);
        super.writeSettings(element);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BadExceptionCaughtVisitor();
    }

    private class BadExceptionCaughtVisitor extends BaseInspectionVisitor {

        private final Set<String> exceptionSet = new HashSet(exceptionList);

        @Override public void visitTryStatement(@NotNull PsiTryStatement statement) {
            super.visitTryStatement(statement);
            final PsiParameter[] catchBlockParameters =
                    statement.getCatchBlockParameters();
            for (PsiParameter parameter : catchBlockParameters) {
                if(parameter == null) {
                    continue;
                }
                final PsiType type = parameter.getType();
                final String text = type.getCanonicalText();
                if (text == null) {
                    continue;
                }
                if (exceptionSet.contains(text)) {
                    final PsiTypeElement typeElement =
                            parameter.getTypeElement();
                    registerError(typeElement);
                }
            }
        }
    }

    private class Form {

        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        ListTable table;

        Form() {
            super();
            removeButton.setAction(new RemoveAction(table));
            addButton.setAction(new AddAction(table));
        }

        private void createUIComponents() {
            table = new ListTable(new ListWrappingTableModel(exceptionList,
                    InspectionGadgetsBundle.message(
                            "exception.class.column.name")));
        }

        public JComponent getContentPanel() {
            return contentPanel;
        }
    }
}