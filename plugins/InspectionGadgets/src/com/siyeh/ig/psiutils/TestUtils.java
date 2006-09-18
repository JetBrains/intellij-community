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
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestUtils {

    private TestUtils() {
        super();
    }

    public static boolean isTest(@NotNull PsiClass aClass) {
        final PsiManager manager = aClass.getManager();
        final PsiFile file = aClass.getContainingFile();
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return true;
        }
        final Project project = manager.getProject();
        final ProjectRootManager rootManager =
                ProjectRootManager.getInstance(project);
        final ProjectFileIndex fileIndex = rootManager.getFileIndex();
        return fileIndex.isInTestSourceContent(virtualFile);
    }

    public static boolean isJUnitTestMethod(@NotNull PsiMethod method) {
        if(AnnotationUtil.isAnnotated(method, "org.junit.Test", true)) {
            return true;
        }
        final String methodName = method.getName();
        @NonNls final String test = "test";
        if (!methodName.startsWith(test)) {
            return false;
        }
        if (method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                !method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return false;
        }
        final PsiType returnType = method.getReturnType();
        if (returnType == null) {
            return false;
        }
        if (!returnType.equals(PsiType.VOID)) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 0) {
            return false;
        }
        final PsiClass targetClass = method.getContainingClass();
        return isJUnitTestClass(targetClass);
    }

    public static boolean isJUnitTestClass(@Nullable PsiClass targetClass){
        return targetClass != null &&
                ClassUtils.isSubclass(targetClass, "junit.framework.TestCase");
    }
}