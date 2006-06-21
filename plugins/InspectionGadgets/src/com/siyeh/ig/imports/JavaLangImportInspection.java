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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

public class JavaLangImportInspection extends ClassInspection {

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "java.lang.import.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "java.lang.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new DeleteImportFix();
    }

    public BaseInspectionVisitor buildVisitor(){
        return new JavaLangImportVisitor();
    }

    private static class JavaLangImportVisitor extends BaseInspectionVisitor {

        public void visitClass(@NotNull PsiClass aClass){
            // no call to super, so it doesn't drill down
            if(!(aClass.getParent() instanceof PsiJavaFile)) {
                return;
            }
            if (PsiUtil.isInJspFile(aClass.getContainingFile())) {
                return;
            }
            final PsiJavaFile file = (PsiJavaFile) aClass.getContainingFile();
            if(!file.getClasses()[0].equals(aClass)){
                return;
            }
            final PsiImportList importList = file.getImportList();
            if(importList == null){
                return;
            }
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            for(PsiImportStatement importStatement : importStatements) {
                checkImportStatment(importStatement, file);
            }
        }

        private void checkImportStatment(PsiImportStatement importStatement,
                                         PsiJavaFile file){
            final PsiJavaCodeReferenceElement reference =
                    importStatement.getImportReference();
            if (reference == null) {
                return;
            }
            final String text = importStatement.getQualifiedName();
            if (text == null) {
                return;
            }
            if(importStatement.isOnDemand()){
                if(HardcodedMethodConstants.JAVA_LANG.equals(text)){
                    registerError(importStatement);
                }
            } else {
                final int classNameIndex = text.lastIndexOf((int) '.');
                if(classNameIndex < 0){
                    return;
                }
                final String parentName =
                        text.substring(0, classNameIndex);
                if (!HardcodedMethodConstants.JAVA_LANG.equals(parentName)) {
                    return;
                }
                if (ImportUtils.hasOnDemandImportConflict(text, file)) {
                    return;
                }
                registerError(importStatement);
            }
        }
    }
}
