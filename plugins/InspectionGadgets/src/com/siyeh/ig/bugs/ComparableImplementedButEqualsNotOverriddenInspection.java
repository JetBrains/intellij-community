/*
 * Copyright 2006 Bas Leijdekkers
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class ComparableImplementedButEqualsNotOverriddenInspection
        extends ClassInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "comparable.implemented.but.equals.not.overridden.display.name");
    }

    @Nls
    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "comparable.implemented.but.equals.not.overridden.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CompareToAndEqualsNotPairedVisitor();
    }

    private static class CompareToAndEqualsNotPairedVisitor
            extends BaseInspectionVisitor {

        public void visitClass(PsiClass aClass) {
            super.visitClass(aClass);
            final PsiMethod[] methods = aClass.findMethodsByName(
                    HardcodedMethodConstants.COMPARE_TO, false);
            if (methods.length == 0) {
                return;
            }
            final PsiManager manager = aClass.getManager();
            final PsiClass comparableClass = manager.findClass(
                    "java.lang.Comparable", aClass.getResolveScope());
            if (comparableClass == null) {
                return;
            }
            final PsiMethod compareToMethod = comparableClass.getMethods()[0];
            boolean foundCompareTo = false;
            for (PsiMethod method : methods) {
                if (MethodSignatureUtil.isSuperMethod(compareToMethod, method)) {
                    foundCompareTo = true;
                    break;
                }
            }
            if (!foundCompareTo) {
                return;
            }
            final PsiMethod[] equalsMethods = aClass.findMethodsByName(
                    HardcodedMethodConstants.EQUALS, false);
            for (PsiMethod equalsMethod : equalsMethods) {
                if (MethodUtils.isEquals(equalsMethod)) {
                    return;
                }
            }
            registerClassError(aClass);
        }
    }
}
