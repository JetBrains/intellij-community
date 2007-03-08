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
package com.siyeh.ig.initialization;

import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.MakeInitializerExplicitFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.InitializationUtils;
import com.siyeh.ig.psiutils.TestUtils;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class InstanceVariableInitializationInspection extends BaseInspection {

    /** @noinspection PublicField */
    public boolean m_ignorePrimitives = false;

    public String getID(){
        return "InstanceVariableMayNotBeInitialized";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "instance.variable.may.not.be.initialized.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        final Boolean junitTestCase = (Boolean)infos[0];
        if (junitTestCase.booleanValue()) {
            return InspectionGadgetsBundle.message(
                    "instance.Variable.may.not.be.initialized.problem.descriptor.junit");
        }
        return InspectionGadgetsBundle.message(
                "instance.variable.may.not.be.initialized.problem.descriptor");
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message("primitive.fields.ignore.option"),
                this, "m_ignorePrimitives");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new MakeInitializerExplicitFix();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new InstanceVariableInitializationVisitor();
    }

    private class InstanceVariableInitializationVisitor
            extends BaseInspectionVisitor{

        public void visitField(@NotNull PsiField field){
            if(field.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if(field.getInitializer() != null){
                return;
            }
            if(m_ignorePrimitives){
                final PsiType fieldType = field.getType();
                if(ClassUtils.isPrimitive(fieldType)){
                    return;
                }
            }
            final PsiClass aClass = field.getContainingClass();
            if(aClass == null){
                return;
            }
            final PsiManager manager = field.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            if(searchHelper.isFieldBoundToForm(field)){
                return;
            }
            if (TestUtils.isJUnitTestClass(aClass)) {
                checkInitializationInSetup(field, aClass);
            } else {
                checkInitializationInConstructors(field, aClass);
            }
        }

        private void checkInitializationInConstructors(PsiField field,
                                                       PsiClass aClass) {
            if(isInitializedInInitializer(field)){
                return;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if(constructors.length == 0){
                registerFieldError(field, Boolean.FALSE);
                return;
            }
            for(final PsiMethod constructor : constructors){
                final PsiCodeBlock body = constructor.getBody();
                if(!InitializationUtils.blockAssignsVariableOrFails(body,
                        field)) {
                    registerFieldError(field, Boolean.FALSE);
                    return;
                }
            }
        }

        private void checkInitializationInSetup(PsiField field,
                                                PsiClass aClass) {
            final PsiMethod setupMethod = getSetupMethod(aClass);
            if (setupMethod == null) {
                return;
            }
            final PsiCodeBlock body = setupMethod.getBody();
            if (InitializationUtils.blockAssignsVariableOrFails(body,
                    field)) {
                return;
            }
            registerFieldError(field, Boolean.TRUE);
        }

        @Nullable
        private PsiMethod getSetupMethod(@NotNull PsiClass aClass) {
            final PsiMethod[] methods =
                    aClass.findMethodsByName("setUp", false);
            for (PsiMethod method : methods) {
                if (method.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }
                final PsiParameterList parameterList =
                        method.getParameterList();
                final PsiParameter[] parameters = parameterList.getParameters();
                if (parameters.length != 0) {
                    continue;
                }
                if (PsiType.VOID.equals(method.getReturnType())) {
                    return method;
                }
            }
            return null;
        }

        private boolean isInitializedInInitializer(@NotNull PsiField field){
            final PsiClass aClass = field.getContainingClass();
            if(aClass == null){
                return false;
            }
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            for(final PsiClassInitializer initializer : initializers){
                if(!initializer.hasModifierProperty(PsiModifier.STATIC)){
                    final PsiCodeBlock body = initializer.getBody();
                    if(InitializationUtils.blockAssignsVariableOrFails(body,
                            field)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}