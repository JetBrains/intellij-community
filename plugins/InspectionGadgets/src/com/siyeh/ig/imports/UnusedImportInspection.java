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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends ClassInspection{
    private final DeleteImportFix fix = new DeleteImportFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("unused.import.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("unused.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new UnusedImportVisitor();
    }

    private static class UnusedImportVisitor extends BaseInspectionVisitor{
        public void visitClass(@NotNull PsiClass aClass){
            if(!(aClass.getParent() instanceof PsiJavaFile)){
                return;
            }
            if(aClass.getContainingFile() instanceof JspFile){
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getParent();
            if(file == null){
                return;
            }
            if(!file.getClasses()[0].equals(aClass)){
                return;
            }
            final PsiImportList importList = file.getImportList();
            if(importList == null){
                return;
            }
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                if(!isNecessaryImport(importStatement, file.getClasses())){
                    registerError(importStatement);
                }
            }
        }

        private static boolean isNecessaryImport(
                PsiImportStatement importStatement, PsiClass[] classes){
            final ImportIsUsedVisitor visitor = new ImportIsUsedVisitor(
                    importStatement);
            for(PsiClass aClasses : classes){
                aClasses.accept(visitor);
                final PsiClass[] innerClasses = aClasses.getInnerClasses();
                for(PsiClass innerClass : innerClasses){
                    innerClass.accept(visitor);
                }
            }
            return visitor.isUsed();
        }
    }
}
