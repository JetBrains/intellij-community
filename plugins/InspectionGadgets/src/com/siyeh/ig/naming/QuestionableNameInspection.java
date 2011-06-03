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

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.ui.ScrollPaneFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.ui.UiUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestionableNameInspection extends BaseInspection {

    /** @noinspection PublicField*/
    @NonNls public String nameString = "aa,abc,bad,bar,bar2,baz,baz1,baz2," +
            "baz3,bb,blah,bogus,bool,cc,dd,defau1t,dummy,dummy2,ee,fa1se," +
            "ff,foo,foo1,foo2,foo3,foobar,four,fred,fred1,fred2,gg,hh,hello," +
            "hello1,hello2,hello3,ii,nu11,one,silly,silly2,string,two,that," +
            "then,three,whi1e,var";

    List<String> nameList = new ArrayList<String>(32);

    public QuestionableNameInspection(){
        parseString(nameString, nameList);
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "questionable.name.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "questionable.name.problem.descriptor");
    }

    @Override
    public void readSettings(Element element) throws InvalidDataException{
        super.readSettings(element);
        parseString(nameString, nameList);
    }

    @Override
    public void writeSettings(Element element) throws WriteExternalException{
        nameString = formatString(nameList);
        super.writeSettings(element);
    }

    @Override
    public JComponent createOptionsPanel(){
        final JPanel panel = new JPanel(new GridBagLayout());
        final ListTable table = new ListTable(new ListWrappingTableModel(
                nameList, InspectionGadgetsBundle.message(
                "questionable.name.column.title")));
        final JScrollPane scrollPane =
                ScrollPaneFactory.createScrollPane(table);
        scrollPane.setMinimumSize(new Dimension(-1, 100));
        final ActionToolbar toolbar =
                UiUtils.createAddRemoveToolbar(table);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(toolbar.getComponent(), constraints);

        constraints.gridy = 1;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, constraints);

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
        return new QuestionableNameVisitor();
    }

    private class QuestionableNameVisitor extends BaseInspectionVisitor{

        private final Set<String> nameSet = new HashSet(nameList);

        @Override public void visitVariable(@NotNull PsiVariable variable){
            final String name = variable.getName();
            if(nameSet.contains(name)){
                registerVariableError(variable);
            }
        }

        @Override public void visitMethod(@NotNull PsiMethod method){
            final String name = method.getName();
            if(nameSet.contains(name)){
                registerMethodError(method);
            }
        }

        @Override public void visitClass(@NotNull PsiClass aClass){
            final String name = aClass.getName();
            if(nameSet.contains(name)){
                registerClassError(aClass);
            }
        }
    }
}