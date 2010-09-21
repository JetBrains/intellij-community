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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiThrowStatement;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.RemoveAction;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BadExceptionThrownInspection extends BaseInspection {

    /**@noinspection PublicField*/
    public String exceptionsString =
      "java.lang.Throwable" + ',' +
      "java.lang.Exception" + ',' +
      "java.lang.Error" + ',' +
      "java.lang.RuntimeException" + ',' +
      "java.lang.NullPointerException" + ',' +
      "java.lang.ClassCastException" + ',' +
      "java.lang.ArrayIndexOutOfBoundsException";

    final List<String> exceptionList = new ArrayList<String>(32);

    public BadExceptionThrownInspection(){
        parseString(exceptionsString, exceptionList);
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(exceptionsString, exceptionList);
    }

    public void writeSettings(Element element) throws WriteExternalException{
        exceptionsString = formatString(exceptionList);
        super.writeSettings(element);
    }

    @NotNull
    public String getID(){
        return "ProhibitedExceptionThrown";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("bad.exception.thrown.display.name");
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiType type = (PsiType)infos[0];
        final String exceptionName = type.getPresentableText();
        return InspectionGadgetsBundle.message(
                "bad.exception.thrown.problem.descriptor", exceptionName);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new BadExceptionThrownVisitor();
    }

    private class BadExceptionThrownVisitor extends BaseInspectionVisitor{

        private final Set<String> exceptionSet = new HashSet(exceptionList);

        @Override public void visitThrowStatement(PsiThrowStatement statement){
            super.visitThrowStatement(statement);
            final PsiExpression exception = statement.getException();
            if(exception == null){
                return;
            }
            final PsiType type = exception.getType();
            if(type == null){
                return;
            }
            final String text = type.getCanonicalText();
            if(exceptionSet.contains(text)){
                registerStatementError(statement, type);
            }
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        ListTable table;

        Form(){
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
        }

        private void createUIComponents() {
            table = new ListTable(new ListWrappingTableModel(exceptionList,
                    InspectionGadgetsBundle.message(
                            "exception.class.column.name")));
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }
}