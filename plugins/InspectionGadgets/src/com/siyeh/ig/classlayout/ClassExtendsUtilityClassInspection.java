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
package com.siyeh.ig.classlayout;

import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;

public class ClassExtendsUtilityClassInspection extends BaseInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "class.extends.utility.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.INHERITANCE_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiClass superClass = (PsiClass) infos[0];
        final String superClassName = superClass.getName();
        return InspectionGadgetsBundle.message(
                "class.extends.utility.class.problem.descriptor", superClassName
        );
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ClassExtendsUtilityClassVisitor();
    }

    private static class ClassExtendsUtilityClassVisitor
            extends BaseInspectionVisitor {

        public void visitClass(PsiClass aClass) {
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            final PsiClass superClass = aClass.getSuperClass();
            if (superClass == null) {
                return;
            }
            if (superClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
                return;
            }
            if (!UtilityClassUtil.isUtilityClass(superClass)) {
                return;
            }
            registerClassError(aClass, superClass);
        }
    }
}