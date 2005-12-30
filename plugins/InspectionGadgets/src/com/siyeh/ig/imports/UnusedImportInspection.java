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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.FileInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;

public class UnusedImportInspection extends FileInspection {

    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("unused.import.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message(
                "unused.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnusedImportVisitor();
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor{

        public void visitJavaFile(PsiJavaFile file) {
            final PsiImportList importList = file.getImportList();
            if (importList == null) {
                return;
            }
            final PsiClass[] classes = file.getClasses();
            final PsiImportStatementBase[] importStatements =
                    importList.getAllImportStatements();
            for (PsiImportStatementBase importStatement : importStatements) {
                if (!isNecessaryImport(importStatement, classes)) {
                    registerError(importStatement);
                }
            }
        }

        private static boolean isNecessaryImport(
                PsiImportStatementBase importStatement, PsiClass[] classes){
            if (importStatement instanceof PsiImportStatement) {
                final PsiImportStatement statement =
                        (PsiImportStatement)importStatement;
                final ImportIsUsedVisitor visitor =
                        new ImportIsUsedVisitor(statement);
                for(PsiClass aClasses : classes){
                    aClasses.accept(visitor);
                }
                return visitor.isUsed();
            } else if (importStatement instanceof PsiImportStaticStatement) {
                final PsiImportStaticStatement statement =
                        (PsiImportStaticStatement)importStatement;
                final StaticImportIsUsedVisitor visitor =
                        new StaticImportIsUsedVisitor(statement);
                for(PsiClass aClasses : classes){
                    aClasses.accept(visitor);
                }
                return visitor.isUsed();
            }
            return false;
        }
    }
}