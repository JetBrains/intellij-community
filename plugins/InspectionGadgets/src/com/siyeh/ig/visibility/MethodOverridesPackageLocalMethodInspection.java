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
package com.siyeh.ig.visibility;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class MethodOverridesPackageLocalMethodInspection
        extends MethodInspection {

    private final RenameFix fix = new RenameFix();

    public String getID() {
        return "MethodOverridesPrivateMethodOfSuperclass";
    }

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("method.overrides.package.local.method.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.VISIBILITY_GROUP_NAME;
    }

    protected InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
        return true;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("method.overrides.package.local.method.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new MethodOverridesPrivateMethodVisitor();
    }

    private static class MethodOverridesPrivateMethodVisitor
            extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters == null){
                return;
            }
            PsiClass ancestorClass = aClass.getSuperClass();
            final Set<PsiClass> visitedClasses = new HashSet<PsiClass>();
            while (ancestorClass != null) {
                if (!visitedClasses.add(ancestorClass)) {
                    return;
                }
                final PsiMethod overridingMethod =
                        ancestorClass.findMethodBySignature(method, true);
                if (overridingMethod != null) {
                    if (overridingMethod.hasModifierProperty(
                            PsiModifier.PACKAGE_LOCAL)) {
                        final PsiJavaFile file =
                                PsiTreeUtil.getParentOfType(aClass,
                                                            PsiJavaFile.class);
                        if (file == null) {
                            return;
                        }
                        final PsiJavaFile ancestorFile =
                                PsiTreeUtil.getParentOfType(ancestorClass,
                                                            PsiJavaFile.class);
                        if (ancestorFile == null) {
                            return;
                        }
                        final String packageName = file.getPackageName();
                        final String ancestorPackageName =
                                ancestorFile.getPackageName();
                        if (!packageName.equals(ancestorPackageName)) {
                            registerMethodError(method);
                            return;
                        }
                    }
                }
                ancestorClass = ancestorClass.getSuperClass();
            }
        }
    }
}