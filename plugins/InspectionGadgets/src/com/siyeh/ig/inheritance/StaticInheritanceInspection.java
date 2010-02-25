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
package com.siyeh.ig.inheritance;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class StaticInheritanceInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "static.inheritance.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "static.inheritance.problem.descriptor");
    }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[]{new StaticInheritanceFix(false), new StaticInheritanceFix(true)};
  }


  public BaseInspectionVisitor buildVisitor() {
        return new StaticInheritanceVisitor();
    }

    private static class StaticInheritanceVisitor
            extends BaseInspectionVisitor {

        @Override public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            final PsiReferenceList implementsList = aClass.getImplementsList();
            if (implementsList == null) {
                return;
            }
            final PsiJavaCodeReferenceElement[] references =
                    implementsList.getReferenceElements();
            for (final PsiJavaCodeReferenceElement reference : references) {
                final PsiClass iface = (PsiClass)reference.resolve();
                if (iface != null) {
                    if (interfaceContainsOnlyConstants(iface, new HashSet<PsiClass>())) {
                        registerError(reference);
                    }
                }
            }
        }

        private static boolean interfaceContainsOnlyConstants(
                PsiClass iface, Set<PsiClass> visitedIntefaces) {
            if (!visitedIntefaces.add(iface)) {
                return true;
            }
            if (iface.getAllFields().length == 0) {
                // ignore it, it's either a true interface or just a marker
                return false;
            }
            if (iface.getMethods().length != 0) {
                return false;
            }
            final PsiClass[] parentInterfaces = iface.getInterfaces();
            for (final PsiClass parentInterface : parentInterfaces) {
                if (!interfaceContainsOnlyConstants(parentInterface,
                        visitedIntefaces)) {
                    return false;
                }
            }
            return true;
        }
    }
}