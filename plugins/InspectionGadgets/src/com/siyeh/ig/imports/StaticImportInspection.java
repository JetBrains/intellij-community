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
package com.siyeh.ig.imports;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class StaticImportInspection extends BaseInspection {

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("static.import.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "static.import.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new StaticImportVisitor();
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        // todo implement me
        return null;
    }

    private static class StaticImportFix extends InspectionGadgetsFix{

        public String getName(){
            return InspectionGadgetsBundle.message(
                    "static.import.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement reference = descriptor.getPsiElement();
            final PsiImportStaticStatement importStatment =
                    (PsiImportStaticStatement) reference.getParent();
            final PsiJavaFile file =
                    (PsiJavaFile) importStatment.getContainingFile();

           // final List references = qualifyReferences(importStatment, file);
         //   importStatment.delete();

          //  for(Object referencesToShorten : references){

          //  }
        }
    }

    private static class StaticImportVisitor extends BaseInspectionVisitor{

        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            if(!(aClass.getParent() instanceof PsiJavaFile)){
                return;
            }
          if (PsiUtil.isInJspFile(aClass.getContainingFile())) {
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
            final PsiImportStaticStatement[] importStatements =
                    importList.getImportStaticStatements();
            for(final PsiImportStaticStatement importStatement :
                    importStatements){
                registerError(importStatement);
            }
        }
    }
}