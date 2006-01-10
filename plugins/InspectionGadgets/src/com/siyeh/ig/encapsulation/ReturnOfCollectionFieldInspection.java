/*
 * Copyright 2003-2005 Dave Griffith
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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class ReturnOfCollectionFieldInspection extends StatementInspection{

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

    public String buildErrorString(PsiElement location){
        final PsiField field = (PsiField) ((PsiReference) location).resolve();
        assert field != null;
        final PsiType type = field.getType();
        if(type.getArrayDimensions() > 0){
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
            extends StatementInspectionVisitor{

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
            if(!CollectionUtils.isArrayOrCollectionField(returnValue)){
                return;
            }
            registerError(returnValue);
        }
    }
}