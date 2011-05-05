/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.util.SpecialAnnotationsUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class EmptyClassInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public final ExternalizableStringSet ignorableAnnotations =
            new ExternalizableStringSet();

    @Override
    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("empty.class.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        final Object element = infos[0];
        if (element instanceof PsiAnonymousClass) {
            return InspectionGadgetsBundle.message(
                    "empty.anonymous.class.problem.descriptor");
        } else if (element instanceof PsiClass){
            return InspectionGadgetsBundle.message(
                    "empty.class.problem.descriptor");
        } else {
            return InspectionGadgetsBundle.message(
                    "empty.class.file.without.class.problem.descriptor");
        }
    }

    @Override
    public JComponent createOptionsPanel() {
        return SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
                ignorableAnnotations,
                InspectionGadgetsBundle.message("ignore.if.annotated.by"));
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new EmptyClassVisitor();
    }

    private class EmptyClassVisitor extends BaseInspectionVisitor {

        @Override public void visitFile(PsiFile file) {
            if (!(file instanceof PsiJavaFile)) {
                return;
            }
            final PsiJavaFile javaFile = (PsiJavaFile)file;
            if (javaFile.getClasses().length != 0) {
                return;
            }
            @NonNls final String fileName = javaFile.getName();
            if ("package-info.java".equals(fileName)) {
                return;
            }
            registerError(file, file);
        }

        @Override public void visitClass(@NotNull PsiClass aClass) {
            //don't call super, to prevent drilldown
            if (JspPsiUtil.isInJspFile(aClass.getContainingFile())) {
                return;
            }
            if (aClass.isInterface() || aClass.isEnum() ||
                    aClass.isAnnotationType()) {
                return;
            }
            if (aClass instanceof PsiTypeParameter) {
                return;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length > 0) {
                return;
            }
            final PsiMethod[] methods = aClass.getMethods();
            if (methods.length > 0) {
                return;
            }
            final PsiField[] fields = aClass.getFields();
            if (fields.length > 0) {
                return;
            }
            final PsiClassInitializer[] initializers = aClass.getInitializers();
            if (initializers.length > 0) {
                return;
            }
            if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations)) {
                return;
            }
            registerClassError(aClass, aClass);
        }
    }
}