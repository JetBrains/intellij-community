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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class ReturnOfCollectionFieldInspection extends BaseInspection{

    /** @noinspection PublicField*/
    public boolean ignorePrivateMethods = true;

    public String getID(){
        return "ReturnOfCollectionOrArrayField";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "return.of.collection.array.field.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.ENCAPSULATION_GROUP_NAME;
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "return.of.collection.array.field.option"), this,
                "ignorePrivateMethods");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final PsiField field = (PsiField)infos[0];
        final PsiType type = field.getType();
        if(type instanceof PsiArrayType){
            return InspectionGadgetsBundle.message(
                    "return.of.collection.array.field.problem.descriptor.array");
        } else{
            return InspectionGadgetsBundle.message(
                    "return.of.collection.array.field.problem.descriptor.collection");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ReturnOfCollectionFieldVisitor();
    }

    private class ReturnOfCollectionFieldVisitor
            extends BaseInspectionVisitor {

        public void visitReturnStatement(@NotNull PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(returnValue == null){
                return;
            }
            final PsiMethod containingMethod =
                    PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
            if (containingMethod == null) {
                return;
            }
            if (ignorePrivateMethods &&
                    containingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiClass returnStatementClass =
                    containingMethod.getContainingClass();
            if (returnStatementClass == null) {
                return;
            }
            if (!(returnValue instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression =
                    (PsiReferenceExpression)returnValue;
            final PsiElement referent = referenceExpression.resolve();
            if(!(referent instanceof PsiField)){
                return;
            }
            final PsiField field = (PsiField) referent;
            final PsiClass fieldClass = field.getContainingClass();
            if (!returnStatementClass.equals(fieldClass)) {
                return;
            }
            if(!CollectionUtils.isArrayOrCollectionField(field)){
                return;
            }
            registerError(returnValue, field);
        }
    }
}