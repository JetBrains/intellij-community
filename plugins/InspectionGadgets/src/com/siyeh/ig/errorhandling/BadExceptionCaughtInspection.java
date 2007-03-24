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

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.AddAction;
import com.siyeh.ig.ui.IGTable;
import com.siyeh.ig.ui.ListWrappingTableModel;
import com.siyeh.ig.ui.RemoveAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BadExceptionCaughtInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public String exceptionCheckString =
            "java.lang.NullPointerException" + ',' +
            "java.lang.IllegalMonitorStateException" + ',' +
            "java.lang.ArrayIndexOutOfBoundsException";

    final List<String> exceptionsList = new ArrayList<String>(32);

    public BadExceptionCaughtInspection() {
        parseExceptionsString();
    }

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
        parseExceptionsString();
    }

    private void parseExceptionsString() {
        final String[] strings = exceptionCheckString.split(",");
        exceptionsList.clear();
        exceptionsList.addAll(Arrays.asList(strings));
    }

    public void writeSettings(Element element) throws WriteExternalException {
        formatExceptionsString();
        super.writeSettings(element);
    }

    private void formatExceptionsString() {
        final StringBuilder buffer = new StringBuilder();
        final int size = exceptionsList.size();
        if (size > 0) {
            buffer.append(exceptionsList.get(0));
            for (int i = 1; i < size; i++) {
                buffer.append(',');
                buffer.append(exceptionsList.get(i));
            }
        }
        exceptionCheckString = buffer.toString();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new BadExceptionCaughtVisitor();
    }

    private class BadExceptionCaughtVisitor extends BaseInspectionVisitor {

        public void visitTryStatement(@NotNull PsiTryStatement statement) {
            System.out.println("statement: " + statement);
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
                if (exceptionsList.contains(text)) {
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
        IGTable table;

        Form() {
            super();
            removeButton.setAction(new RemoveAction(table));
            addButton.setAction(new AddAction(table));
        }

        public JComponent getContentPanel() {
            return contentPanel;
        }

        private void createUIComponents() {
            table = new IGTable(new ListWrappingTableModel(exceptionsList,
                    InspectionGadgetsBundle.message(
                            "exception.class.column.name")));
        }
    }
}