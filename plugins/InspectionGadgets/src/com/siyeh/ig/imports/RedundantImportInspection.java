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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.DeleteImportFix;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class RedundantImportInspection extends ClassInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.import.display.name");
    }

    @NotNull
    public String getGroupDisplayName() {
        return GroupNames.IMPORTS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "redundant.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new DeleteImportFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantImportVisitor();
    }

    private static class RedundantImportVisitor extends BaseInspectionVisitor {

        public void visitFile(PsiFile file) {
            super.visitFile(file);
            if (!(file instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile javaFile = (PsiJavaFile)file;
            if (PsiUtil.isInJspFile(file)) {
                return;
            }
            final PsiImportList importList = javaFile.getImportList();
            if (importList == null) {
                return;
            }
            checkNonStaticImports(importList, javaFile);
            checkStaticImports(importList, javaFile);
        }

        private void checkStaticImports(PsiImportList importList,
                                        PsiJavaFile javaFile) {
            final PsiImportStaticStatement[] importStaticStatements =
                    importList.getImportStaticStatements();
            final Set<String> staticImports =
                    new HashSet<String>(importStaticStatements.length);
            for (PsiImportStaticStatement importStaticStatement :
                    importStaticStatements) {
                final String referenceName =
                        importStaticStatement.getReferenceName();
                final PsiClass targetClass =
                        importStaticStatement.resolveTargetClass();
                if (targetClass == null) {
                    continue;
                }
                final String qualifiedName = targetClass.getQualifiedName();
                if (referenceName == null) {
                    if (staticImports.contains(qualifiedName)) {
                        registerError(importStaticStatement);
                        continue;
                    }
                    staticImports.add(qualifiedName);
                } else {
                    final String qualifiedReferenceName =
                            qualifiedName + '.' + referenceName;
                    if (staticImports.contains(qualifiedReferenceName)) {
                        registerError(importStaticStatement);
                        continue;
                    }
                    if (staticImports.contains(qualifiedName)) {
                        if (!ImportUtils.hasOnDemandImportConflict(
                                qualifiedReferenceName, javaFile)) {
                            registerError(importStaticStatement);
                        }
                    }
                    staticImports.add(qualifiedReferenceName);
                }
            }
        }

        private void checkNonStaticImports(PsiImportList importList,
                                           PsiJavaFile javaFile) {
            final PsiImportStatement[] importStatements =
                    importList.getImportStatements();
            final Set<String> imports =
                    new HashSet<String>(importStatements.length);
            for(final PsiImportStatement importStatement : importStatements) {
                final String text = importStatement.getQualifiedName();
                if(text == null) {
                    continue;
                }
                if(imports.contains(text)) {
                    registerError(importStatement);
                    continue;
                }
                if(!importStatement.isOnDemand()) {
                    final PsiElement element = importStatement.resolve();
                    if (!(element instanceof PsiClass)) {
                        continue;
                    }
                    final PsiClass targetClass = (PsiClass)element;
                    final PsiJavaFile targetFile =
                            PsiTreeUtil.getParentOfType(targetClass,
                                    PsiJavaFile.class);
                    if (targetFile == null) {
                        continue;
                    }
                    final String parentName = targetFile.getPackageName();
                    if(imports.contains(parentName)) {
                        if(!ImportUtils.hasOnDemandImportConflict(text,
                                javaFile)) {
                            registerError(importStatement);
                        }
                    }
                }
                imports.add(text);
            }
        }
    }
}