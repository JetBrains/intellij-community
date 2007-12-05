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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NotNull;

public class SamePackageImportInspection extends BaseInspection {

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "import.from.same.package.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "import.from.same.package.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new DeleteImportFix();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new SamePackageImportVisitor();
    }

    private static class SamePackageImportVisitor extends BaseInspectionVisitor{

        @Override public void visitClass(@NotNull PsiClass aClass){
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
            final String packageName = file.getPackageName();
            final PsiImportList importList = file.getImportList();
            if(importList == null){
                return;
            }
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            for(final PsiImportStatement importStatement : importStatements){
                final PsiJavaCodeReferenceElement reference =
                        importStatement.getImportReference();
                if(reference != null){
                    final String text = importStatement.getQualifiedName();
                    if(importStatement.isOnDemand()){
                        if(packageName.equals(text)){
                            registerError(importStatement);
                        }
                    } else {
                        if (text == null) {
                            return;
                        }
                        final int classNameIndex = text.lastIndexOf((int)'.');
                        final String parentName;
                        if (classNameIndex < 0) {
                            parentName = "";
                        } else {
                            parentName = text.substring(0, classNameIndex);
                        }
                        if (packageName.equals(parentName)) {
                            registerError(importStatement);
                        }
                    }
                }
            }
        }
    }
}
