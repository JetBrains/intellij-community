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
package com.siyeh.ig.junit;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class TestCaseWithNoTestMethodsInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreSupers = false;

    @NotNull
    public String getID() {
        return "JUnitTestCaseWithNoTests";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "test.case.with.no.test.methods.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "test.case.with.no.test.methods.problem.descriptor");
    }

    @Nullable
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel("Ignore test cases which have super classes with test methods", this, "ignoreSupers");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TestCaseWithNoTestMethodsVisitor();
    }

    private class TestCaseWithNoTestMethodsVisitor
            extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            if (aClass.isInterface()
                    || aClass.isEnum()
                    || aClass.isAnnotationType()
                    || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            if (!ClassUtils.isSubclass(aClass, "junit.framework.TestCase")) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            for (final PsiMethod method : methods) {
                if (TestUtils.isJUnitTestMethod(method)) {
                    return;
                }
            }
            if (ignoreSupers) {
                final PsiClass superClass = aClass.getSuperClass();
                if (superClass != null) {
                    final PsiMethod[] superMethods = superClass.getMethods();
                    for (PsiMethod superMethod : superMethods) {
                        if (TestUtils.isJUnitTestMethod(superMethod)) {
                            return;
                        }
                    }
                }
            }
            registerClassError(aClass);
        }
    }
}