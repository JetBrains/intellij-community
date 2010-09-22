/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.*;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BadExceptionDeclaredInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public String exceptionsString =
      "java.lang.Throwable" + ',' +
      "java.lang.Exception" + ',' +
      "java.lang.Error" + ',' +
      "java.lang.RuntimeException" + ',' +
      "java.lang.NullPointerException" + ',' +
      "java.lang.ClassCastException" + ',' +
      "java.lang.ArrayIndexOutOfBoundsException";

    /** @noinspection PublicField*/
    public boolean ignoreTestCases = false;
    final List<String> exceptionList = new ArrayList<String>(32);

    public BadExceptionDeclaredInspection() {
        parseString(exceptionsString, exceptionList);
    }

    @NotNull
    public String getID(){
        return "ProhibitedExceptionDeclared";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "bad.exception.declared.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "bad.exception.declared.problem.descriptor");
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(exceptionsString, exceptionList);
    }

    public void writeSettings(Element element) throws WriteExternalException{
        exceptionsString = formatString(exceptionList);
        super.writeSettings(element);
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new BadExceptionDeclaredVisitor();
    }

    private class BadExceptionDeclaredVisitor extends BaseInspectionVisitor{

        private final Set<String> exceptionSet = new HashSet(exceptionList);

        @Override public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            if(ignoreTestCases){
                if(TestUtils.isJUnitTestMethod(method)){
                    return;
                }
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiJavaCodeReferenceElement[] references =
                    throwsList.getReferenceElements();
            for(PsiJavaCodeReferenceElement reference : references){
                final PsiElement element = reference.resolve();
                if (!(element instanceof PsiClass)) {
                    continue;
                }
                final PsiClass thrownClass = (PsiClass)element;
                final String qualifiedName = thrownClass.getQualifiedName();
                if (qualifiedName != null &&
                        exceptionSet.contains(qualifiedName)) {
                    registerError(reference);
                }
            }
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        JCheckBox ignoreTestCasesCheckBox;
        ListTable table;

        Form(){
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
            ignoreTestCasesCheckBox.setAction(new ToggleAction(
                    InspectionGadgetsBundle.message(
                            "bad.exception.declared.ignore.exceptions.declared.in.junit.test.cases.option"),
                    BadExceptionDeclaredInspection.this, "ignoreTestCases"));
            ignoreTestCasesCheckBox.setSelected(ignoreTestCases);
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