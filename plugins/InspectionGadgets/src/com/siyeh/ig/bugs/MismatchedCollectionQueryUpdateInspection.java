/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ui.ListTable;
import com.intellij.codeInspection.ui.ListWrappingTableModel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ig.ui.ExternalizableStringSet;
import com.siyeh.ig.ui.UiUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class MismatchedCollectionQueryUpdateInspection
        extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet queryNames =
            new ExternalizableStringSet("copyInto", "drainTo", "propertyNames",
                    "save", "store", "write");
    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet updateNames =
            new ExternalizableStringSet("add", "clear", "drainTo", "insert",
                    "load", "offer", "poll", "push", "put", "remove", "replace",
                    "retain", "set", "take");

    public MismatchedCollectionQueryUpdateInspection() {}

    @Override
    @NotNull
    public String getID(){
        return "MismatchedQueryAndUpdateOfCollection";
    }

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "mismatched.update.collection.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        final boolean updated = ((Boolean)infos[0]).booleanValue();
        if(updated){
            return InspectionGadgetsBundle.message(
                    "mismatched.update.collection.problem.descriptor.updated.not.queried");
        } else{
            return InspectionGadgetsBundle.message(
                    "mismatched.update.collection.problem.description.queried.not.updated");
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        final JPanel panel = new JPanel(new GridBagLayout());
        final ListTable table1 =
                new ListTable(new ListWrappingTableModel(queryNames,
                        InspectionGadgetsBundle.message("query.column.name")));
        final JScrollPane scrollPane1 =
                ScrollPaneFactory.createScrollPane(table1);
        final ActionToolbar toolbar1 = UiUtils.createAddRemoveToolbar(table1);

        final ListTable table2 =
                new ListTable(new ListWrappingTableModel(updateNames,
                        InspectionGadgetsBundle.message("update.column.name")));
        final JScrollPane scrollPane2 =
                ScrollPaneFactory.createScrollPane(table2);
        final ActionToolbar toolbar2 = UiUtils.createAddRemoveToolbar(table2);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets.left = 4;
        constraints.insets.right = 4;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(toolbar1.getComponent(), constraints);

        constraints.gridx = 1;
        panel.add(toolbar2.getComponent(), constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        constraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane1, constraints);

        constraints.gridx = 1;
        panel.add(scrollPane2, constraints);
        return panel;
    }

    @Override
    public boolean isEnabledByDefault(){
        return true;
    }

    @Override
    public boolean runForWholeFile() {
        return true;
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new MismatchedCollectionQueryUpdateVisitor();
    }

    private static boolean isEmptyCollectionInitializer(
            PsiExpression initializer){
        if(!(initializer instanceof PsiNewExpression)){
            return false;
        }
        final PsiNewExpression newExpression =
                (PsiNewExpression) initializer;
        final PsiExpressionList argumentList =
                newExpression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] arguments = argumentList.getExpressions();
        for(final PsiExpression argument : arguments){
            final PsiType argumentType = argument.getType();
            if(argumentType == null){
                return false;
            }
            if(CollectionUtils.isCollectionClassOrInterface(argumentType)){
                return false;
            }
            if(argumentType instanceof PsiArrayType){
                return false;
            }
        }
        return true;
    }

    private class MismatchedCollectionQueryUpdateVisitor
            extends BaseInspectionVisitor{

        @Override public void visitField(@NotNull PsiField field){
            super.visitField(field);
            if(!field.hasModifierProperty(PsiModifier.PRIVATE)){
                return;
            }
            final PsiClass containingClass = PsiUtil.getTopLevelClass(field);
            if (!checkVariable(field, containingClass)) {
                return;
            }
            final boolean written =
                    collectionContentsAreUpdated(field, containingClass);
            final boolean read =
                    collectionContentsAreQueried(field, containingClass);
            if(read == written){
                return;
            }
            registerFieldError(field, Boolean.valueOf(written));
        }

        @Override public void visitLocalVariable(
                @NotNull PsiLocalVariable variable){
            super.visitLocalVariable(variable);
            final PsiCodeBlock codeBlock =
                    PsiTreeUtil.getParentOfType(variable,
                            PsiCodeBlock.class);
            if (!checkVariable(variable, codeBlock)) {
                return;
            }
            final boolean written =
                    collectionContentsAreUpdated(variable, codeBlock);
            final boolean read =
                    collectionContentsAreQueried(variable, codeBlock);
            if(read != written){
                registerVariableError(variable, Boolean.valueOf(written));
            }
        }

        private boolean checkVariable(PsiVariable variable,
                                      PsiElement context) {
            if (context == null) {
                return false;
            }
            final PsiType type = variable.getType();
            if(!CollectionUtils.isCollectionClassOrInterface(type)){
                return false;
            }
            if(VariableAccessUtils.variableIsAssignedFrom(variable, context)){
                return false;
            }
            if(VariableAccessUtils.variableIsReturned(variable, context)){
                return false;
            }
            if(VariableAccessUtils.variableIsPassedAsMethodArgument(variable,
                    context)){
                return false;
            }
            return !VariableAccessUtils.variableIsUsedInArrayInitializer(
                    variable, context);
        }

        private boolean collectionContentsAreUpdated(
                PsiVariable variable, PsiElement context){
            if(collectionUpdateCalled(variable, context)){
                return true;
            }
            final PsiExpression initializer = variable.getInitializer();
            if(initializer != null &&
                    !isEmptyCollectionInitializer(initializer)){
                return true;
            }
            if(initializer instanceof PsiNewExpression){
                final PsiNewExpression newExpression =
                        (PsiNewExpression)initializer;
                final PsiAnonymousClass anonymousClass =
                        newExpression.getAnonymousClass();
                if(anonymousClass != null){
                    if(collectionUpdateCalled(variable, anonymousClass)){
                        return true;
                    }
                }
            }
            return VariableAccessUtils.variableIsAssigned(variable, context);
        }

        private boolean collectionContentsAreQueried(
                PsiVariable variable, PsiElement context){
            if(collectionQueryCalled(variable, context)){
                return true;
            }
            final PsiExpression initializer = variable.getInitializer();
            if(initializer != null &&
                    !isEmptyCollectionInitializer(initializer)){
                return true;
            }
            return collectionQueriedByAssignment(variable, context);
        }

        private boolean collectionQueryCalled(PsiVariable variable,
                                                     PsiElement context){
            final CollectionQueryCalledVisitor visitor =
                    new CollectionQueryCalledVisitor(variable, queryNames);
            context.accept(visitor);
            return visitor.isQueried();
        }

        private boolean collectionUpdateCalled(PsiVariable variable,
                                                      PsiElement context){
            final CollectionUpdateCalledVisitor visitor =
                    new CollectionUpdateCalledVisitor(variable, updateNames);
            context.accept(visitor);
            return visitor.isUpdated();
        }
    }

    private static boolean collectionQueriedByAssignment(
            @NotNull PsiVariable variable, @NotNull PsiElement context) {
        final CollectionQueriedByAssignmentVisitor visitor =
                new CollectionQueriedByAssignmentVisitor(variable);
        context.accept(visitor);
        return visitor.mayBeQueried();
    }

    private static class CollectionQueriedByAssignmentVisitor
            extends JavaRecursiveElementVisitor{

        private boolean mayBeQueried = false;
        @NotNull private final PsiVariable variable;

        CollectionQueriedByAssignmentVisitor(@NotNull PsiVariable variable){
            this.variable = variable;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if (mayBeQueried) {
                return;
            }
            super.visitElement(element);
        }

        @Override public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            if(mayBeQueried){
                return;
            }
            super.visitAssignmentExpression(assignment);
            final PsiExpression lhs = assignment.getLExpression();
            if (!VariableAccessUtils.mayEvaluateToVariable(lhs, variable)) {
                return;
            }
            final PsiExpression rhs = assignment.getRExpression();
            if (isEmptyCollectionInitializer(rhs)) {
                return;
            }
            mayBeQueried = true;
        }

        public boolean mayBeQueried(){
            return mayBeQueried;
        }
    }
}
