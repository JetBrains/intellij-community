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
package com.siyeh.ig.naming;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.AddAction;
import com.siyeh.ig.ui.IGTable;
import com.siyeh.ig.ui.ListWrappingTableModel;
import com.siyeh.ig.ui.RemoveAction;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    @NonNls public String questionString =
            "is,can,has,should,could,will,shall,check,contains,equals," +
            "startsWith,endsWith";

    List<String> questionList = new ArrayList(32);

    public NonBooleanMethodNameMayNotStartWithQuestionInspection(){
        parseString(questionString, questionList);
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "non.boolean.method.name.must.not.start.with.question.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "non.boolean.method.name.must.not.start.with.question.problem.descriptor");
    }

    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(questionString, questionList);
    }

    public void writeSettings(Element element) throws WriteExternalException{
        questionString = formatString(questionList);
        super.writeSettings(element);
    }

    public JComponent createOptionsPanel(){
        final Form form = new Form();
        return form.getContentPanel();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new RenameFix();
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new NonBooleanMethodNameMayNotStartWithQuestionVisitor();
    }

    private class NonBooleanMethodNameMayNotStartWithQuestionVisitor
            extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            final PsiType returnType = method.getReturnType();
            if(returnType == null || returnType.equals(PsiType.BOOLEAN)){
                return;
            }
            final String name = method.getName();
            boolean startsWithQuestionWord = false;
            for(String question : questionList){
                if(name.startsWith(question)){
                    if (name.length() == question.length()){
                        startsWithQuestionWord = true;
                        break;
                    }
                    final char nextChar = name.charAt(question.length());
                    if(Character.isUpperCase(nextChar) || nextChar == '_'){
                        startsWithQuestionWord = true;
                        break;
                    }
                }
            }
            if(!startsWithQuestionWord ||
                    LibraryUtil.isOverrideOfLibraryMethod(method)){
                return;
            }
            registerMethodError(method);
        }
    }

    private class Form{

        JPanel contentPanel;
        JButton addButton;
        JButton removeButton;
        IGTable table;

        Form(){
            super();
            addButton.setAction(new AddAction(table));
            removeButton.setAction(new RemoveAction(table));
        }

        private void createUIComponents(){
            table = new IGTable(new ListWrappingTableModel(questionList,
                    InspectionGadgetsBundle.message(
                            "boolean.method.name.must.start.with.question.table.column.name")));
        }

        public JComponent getContentPanel(){
            return contentPanel;
        }
    }
}