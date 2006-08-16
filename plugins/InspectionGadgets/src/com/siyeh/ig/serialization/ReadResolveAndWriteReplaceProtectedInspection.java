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
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.MakeProtectedFix;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ReadResolveAndWriteReplaceProtectedInspection
        extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "readresolve.writereplace.protected.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "readresolve.writereplace.protected.problem.descriptor");

    }

    public BaseInspectionVisitor buildVisitor() {
        return new ReadResolveWriteReplaceProtectedVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new MakeProtectedFix();
    }

    private static class ReadResolveWriteReplaceProtectedVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // no call to super, so it doesn't drill down
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null) {
                return;
            }
            if (aClass.isInterface() || aClass.isAnnotationType()) {
                return;
            }
            if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
                return;
            }
            if(aClass.hasModifierProperty(PsiModifier.FINAL) &&
                   method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            if(!SerializationUtils.isReadResolve(method) &&
                       !SerializationUtils.isWriteReplace(method)) {
                return;
            }
            if(!SerializationUtils.isSerializable(aClass)) {
                return;
            }
            registerMethodError(method);
        }
    }
}