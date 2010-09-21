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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ui.AddAction;
import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.codeInspection.ui.RemoveAction;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.ui.ScrollPaneFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.ui.*;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NonBooleanMethodNameMayNotStartWithQuestionInspection
        extends BaseInspection {

    /** @noinspection PublicField*/
    @NonNls public String questionString =
            "is,can,has,should,could,will,shall,check,contains,equals," +
            "startsWith,endsWith";

    @SuppressWarnings({"PublicField"})
    public boolean ignoreBooleanMethods = false;

    List<String> questionList = new ArrayList(32);

    public NonBooleanMethodNameMayNotStartWithQuestionInspection(){
        parseString(questionString, questionList);
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "non.boolean.method.name.must.not.start.with.question.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "non.boolean.method.name.must.not.start.with.question.problem.descriptor");
    }

    @Override
    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(questionString, questionList);
    }

    @Override
    public void writeSettings(Element element) throws WriteExternalException{
        questionString = formatString(questionList);
        super.writeSettings(element);
    }

    @Override
    public JComponent createOptionsPanel(){
        final JPanel panel = new JPanel(new GridBagLayout());
        final ListTable table =
                new ListTable(new ListWrappingTableModel(questionList,
                        InspectionGadgetsBundle.message(
                                "boolean.method.name.must.start.with.question.table.column.name")));
      final JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(table);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridheight = 3;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, constraints);

        final JButton addButton = new JButton(new AddAction(table));
        constraints.gridx = 1;
        constraints.gridheight = 1;
        constraints.weightx = 0.0;
        constraints.weighty = 0.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(addButton, constraints);

        final JButton removeButton = new JButton(new RemoveAction(table));
        constraints.gridy = 1;
        panel.add(removeButton, constraints);

        final BlankFiller filler = new BlankFiller();
        constraints.gridy = 2;
        constraints.weighty = 1.0;
        panel.add(filler, constraints);

        final CheckBox checkBox =
                new CheckBox(InspectionGadgetsBundle.message(
                        "ignore.methods.with.boolean.return.type.option"),
                        this, "ignoreBooleanMethods");
        constraints.gridy = 3;
        constraints.gridx = 0;
        constraints.gridwidth = 2;
        constraints.weighty = 0.0;
        panel.add(checkBox, constraints);
        return panel;
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos){
        return new RenameFix();
    }

    @Override
    protected boolean buildQuickFixesOnlyForOnTheFlyErrors(){
        return true;
    }

    @Override
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
            if(ignoreBooleanMethods && returnType.equalsToText(
                    "java.lang.Boolean")){
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
}
