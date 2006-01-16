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
package com.siyeh.ig.imports;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import org.jetbrains.annotations.NotNull;

public class OnDemandImportInspection extends ClassInspection {

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("import.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("import.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new PackageImportVisitor();
    }

    private static class PackageImportVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass) {
            // no call to super, so it doesn't drill down
            final PsiElement parent = aClass.getParent();
            if (!(parent instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) parent;
            if(aClass.getContainingFile() instanceof JspFile) {
                return;
            }
            if (!file.getClasses()[0].equals(aClass)) {
                return;
            }
            final PsiImportList importList = file.getImportList();
            if (importList != null) {
                final PsiImportStatement[] importStatements =
                        importList.getImportStatements();
                for(PsiImportStatement importStatement : importStatements){
                    if(importStatement.isOnDemand()){
                        registerError(importStatement);
                    }
                }
            }
        }
    }
}