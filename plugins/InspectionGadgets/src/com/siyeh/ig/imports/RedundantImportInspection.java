/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class RedundantImportInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "redundant.import.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "redundant.import.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(Object... infos) {
        return new DeleteImportFix();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new RedundantImportVisitor();
    }

    private static class RedundantImportVisitor extends BaseInspectionVisitor {

        @Override public void visitFile(PsiFile file) {
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
            final Set<String> onDemandImports = new HashSet();
            final Set<String> singleClassImports = new HashSet();
            for(final PsiImportStatement importStatement : importStatements) {
                final String qualifiedName = importStatement.getQualifiedName();
                if(qualifiedName == null) {
                    continue;
                }
                if (importStatement.isOnDemand()) {
                    if (onDemandImports.contains(qualifiedName)) {
                        registerError(importStatement);
                    }
                    onDemandImports.add(qualifiedName);
                } else {
                    if (singleClassImports.contains(qualifiedName)) {
                        registerError(importStatement);
                        continue;
                    }
                    final PsiElement element = importStatement.resolve();
                    if (!(element instanceof PsiClass)) {
                        continue;
                    }
	                final PsiElement context = element.getContext();
                    if (context == null) {
                        continue;
                    }
                    final String contextName;
                    if (context instanceof PsiJavaFile) {
	                    final PsiJavaFile file = (PsiJavaFile)context;
	                    contextName = file.getPackageName();
                    } else if (context instanceof PsiClass) {
                        final PsiClass aClass = (PsiClass)context;
                        contextName = aClass.getQualifiedName();
                    } else {
                        continue;
                    }
                    if (onDemandImports.contains(contextName) &&
                            !ImportUtils.hasOnDemandImportConflict(qualifiedName,
                                    javaFile)) {
                        registerError(importStatement);
                    }
                    singleClassImports.add(qualifiedName);
                }
            }
        }
    }
}