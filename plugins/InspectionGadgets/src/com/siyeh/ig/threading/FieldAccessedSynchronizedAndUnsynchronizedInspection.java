/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Set;

public class FieldAccessedSynchronizedAndUnsynchronizedInspection
        extends ClassInspection{

    /** @noinspection PublicField*/
    public boolean countGettersAndSetters = false;

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "field.accessed.synchronized.and.unsynchronized.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "field.accessed.synchronized.and.unsynchronized.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
          InspectionGadgetsBundle.message(
                  "field.accessed.synchronized.and.unsynchronized.option"),
                this, "countGettersAndSetters");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new FieldAccessedSynchronizedAndUnsynchronizedVisitor();
    }

    private class FieldAccessedSynchronizedAndUnsynchronizedVisitor
            extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            if(!containsSynchronization(aClass)){
                return;
            }
            final VariableAccessVisitor visitor =
                    new VariableAccessVisitor(aClass, countGettersAndSetters);
            aClass.accept(visitor);
            final Set<PsiField> fields =
                    visitor.getInappropriatelyAccessedFields();
            for(final PsiField field  : fields){
                if(!field.hasModifierProperty(PsiModifier.FINAL)){
                    final PsiClass containingClass = field.getContainingClass();
                    if(aClass.equals(containingClass)){
                        registerFieldError(field);
                    }
                }
            }
        }

        private boolean containsSynchronization(PsiClass aClass){
            final ContainsSynchronizationVisitor visitor =
                    new ContainsSynchronizationVisitor();
            aClass.accept(visitor);
            return visitor.containsSynchronization();
        }
    }
}