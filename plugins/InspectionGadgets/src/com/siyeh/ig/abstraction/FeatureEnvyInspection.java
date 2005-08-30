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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class FeatureEnvyInspection extends MethodInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("feature.envy.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(Object arg) {
        final String className = ((PsiNamedElement) arg).getName();
        return InspectionGadgetsBundle.message("feature.envy.problem.descriptor", className);
    }


    public BaseInspectionVisitor buildVisitor() {
        return new FeatureEnvyVisitor();
    }

    private static class FeatureEnvyVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            final PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            final ClassAccessVisitor visitor = new ClassAccessVisitor(containingClass);
            method.accept(visitor);
            final Set<PsiClass> overaccessedClasses = visitor.getOveraccessedClasses();
            for(PsiClass aClass : overaccessedClasses){
                registerError(nameIdentifier, aClass);
            }
        }

    }

}
