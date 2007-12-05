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
package com.siyeh.ig.serialization;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class SerializableWithUnconstructableAncestorInspection
        extends BaseInspection {

    @NotNull
    public String getID() {
        return "SerializableClassWithUnconstructableAncestor";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "serializable.with.unconstructable.ancestor.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final PsiClass ancestor = (PsiClass)infos[0];
        return InspectionGadgetsBundle.message(
                "serializable.with.unconstructable.ancestor.problem.descriptor",
                ancestor.getName());
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SerializableWithUnconstructableAncestorVisitor();
    }

    private static class SerializableWithUnconstructableAncestorVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            PsiClass ancestor = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>(16);
            while (ancestor != null &&
                    SerializationUtils.isSerializable(ancestor)) {
                ancestor = ancestor.getSuperClass();
                if (!visitedClasses.add(ancestor)) {
                    return;
                }
            }
            if (ancestor == null) {
                return;  // can't happen, since Object isn't serializable,
                //// but I don't trust the PSI as far as I can throw it
            }
            if (classHasNoArgConstructor(ancestor)) {
                return;
            }
            registerClassError(aClass, ancestor);
        }

        private static boolean classHasNoArgConstructor(PsiClass ancestor) {
            boolean hasConstructor = false;
            boolean hasNoArgConstructor = false;
            final PsiMethod[] methods = ancestor.getMethods();
            for (final PsiMethod method : methods) {
                if (method.isConstructor()) {
                    hasConstructor = true;
                    final PsiParameterList parameterList =
                            method.getParameterList();
                    if (parameterList.getParametersCount() == 0 &&
                            (method.hasModifierProperty(PsiModifier.PUBLIC) ||
                                    method.hasModifierProperty(
                                            PsiModifier.PROTECTED))) {
                        hasNoArgConstructor = true;
                    }
                }
            }
            return hasNoArgConstructor || !hasConstructor;
        }
    }
}