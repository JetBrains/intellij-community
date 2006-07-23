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
package com.siyeh.ig.imports;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.codeInspection.ProblemsHolder;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FileInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends FileInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("unused.import.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "unused.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new DeleteImportFix();
    }

    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly) {
        //if (isOnTheFly) {
        //    return null;
        //}
        return super.buildVisitor(holder, isOnTheFly);
    }

    public BaseInspectionVisitor buildVisitor() {
        return new UnusedImportVisitor();
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor {

        public void visitJavaFile(PsiJavaFile file) {
            final PsiImportList importList = file.getImportList();
            if (importList == null) {
                return;
            }
            final PsiClass[] classes = file.getClasses();
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            checkImports(importStatements, classes);
            final PsiImportStaticStatement[] importStaticStatements =
                    importList.getImportStaticStatements();
            checkStaticImports(importStaticStatements, classes);
        }

        private void checkStaticImports(
                PsiImportStaticStatement[] importStaticStatements,
                PsiClass[] classes) {
            final StaticImportsAreUsedVisitor visitor =
                    new StaticImportsAreUsedVisitor(importStaticStatements);
            for (PsiClass aClass : classes) {
                aClass.accept(visitor);
            }
            final PsiImportStaticStatement[] unusedImportStaticStatements =
                    visitor.getUnusedImportStaticStatements();
            for (PsiImportStaticStatement importStaticStatement :
                    unusedImportStaticStatements) {
                registerError(importStaticStatement);
            }
        }

        private void checkImports(PsiImportStatement[] importStatements,
                                  PsiClass[] classes) {
            final ImportsAreUsedVisitor visitor =
                    new ImportsAreUsedVisitor(importStatements);
            for (PsiClass aClass : classes) {
                aClass.accept(visitor);
            }
            final PsiImportStatement[] unusedImportStatements =
                    visitor.getUnusedImportStatements();
            for (PsiImportStatement unusedImportStatement :
                    unusedImportStatements) {
                registerError(unusedImportStatement);
            }
        }
    }
}