/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MissingOverrideAnnotationInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreObjectMethods = true;

    @Override
    @NotNull
    public String getID() {
        return "override";
    }

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "missing.override.annotation.display.name");
    }

    @Override
    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "missing.override.annotation.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        return new SingleCheckboxOptionsPanel(
                InspectionGadgetsBundle.message(
                        "ignore.equals.hashcode.and.tostring"), this,
                "ignoreObjectMethods");
    }

    @Override
    protected InspectionGadgetsFix buildFix(Object... infos) {
        return new MissingOverrideAnnotationFix();
    }

    private static class MissingOverrideAnnotationFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "missing.override.annotation.add.quickfix");
        }

        @Override
        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement identifier = descriptor.getPsiElement();
            final PsiElement parent = identifier.getParent();
            if (!(parent instanceof PsiModifierListOwner)) {
                return;
            }
            final PsiModifierListOwner modifierListOwner =
                    (PsiModifierListOwner)parent;
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory factory = psiFacade.getElementFactory();
            final PsiAnnotation annotation =
                    factory.createAnnotationFromText("@java.lang.Override",
                            modifierListOwner);
            final PsiModifierList modifierList =
                    modifierListOwner.getModifierList();
            if (modifierList == null) {
                return;
            }
            modifierList.addAfter(annotation, null);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new MissingOverrideAnnotationVisitor();
    }

    private class MissingOverrideAnnotationVisitor
            extends BaseInspectionVisitor{

        @Override
        public void visitMethod(@NotNull PsiMethod method){
            if (method.getNameIdentifier() == null) {
                return;
            }
            if(method.isConstructor()){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE) ||
               method.hasModifierProperty(PsiModifier.STATIC)){
                return;
            }
            if(!PsiUtil.isLanguageLevel5OrHigher(method)){
                return;
            }
            final boolean useJdk6Rules =
                    PsiUtil.isLanguageLevel6OrHigher(method);
            if(useJdk6Rules){
                if(!isJdk6Override(method)){
                    return;
                }
            } else if (!isJdk5Override(method)){
                return;
            }

            if(ignoreObjectMethods && (MethodUtils.isHashCode(method) ||
                    MethodUtils.isEquals(method) ||
                    MethodUtils.isToString(method))){
                return;
            }

            if(hasOverrideAnnotation(method)){
                return;
            }
            registerMethodError(method);
        }

        private boolean hasOverrideAnnotation(
                PsiModifierListOwner element){
            final PsiModifierList modifierList = element.getModifierList();
            if(modifierList == null){
                return false;
            }
            final PsiAnnotation annotation =
                    modifierList.findAnnotation("java.lang.Override");
            return annotation != null;
        }

        private boolean isJdk6Override(PsiMethod method) {
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return false;
            }

            final PsiMethod[] superMethods =
                    getSuperMethodsInJavaSense(method, methodClass);
            if (superMethods.length <= 0) {
                return false;
            }
            // is override except if this is an interface method
            // overriding a protected method in java.lang.Object
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6501053
            if (!methodClass.isInterface()) {
                return true;
            }
            for (PsiMethod superMethod : superMethods) {
                if (!superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isJdk5Override(PsiMethod method) {
            final PsiClass methodClass = method.getContainingClass();
            if (methodClass == null) {
                return false;
            }
            final PsiMethod[] superMethods =
                    getSuperMethodsInJavaSense(method, methodClass);
            for (PsiMethod superMethod : superMethods) {
                final PsiClass superClass = superMethod.getContainingClass();
                if (superClass == null) {
                    continue;
                }
                if (superClass.isInterface()) {
                    continue;
                }
                if (methodClass.isInterface() &&
                        superMethod.hasModifierProperty(PsiModifier.PROTECTED)) {
                    // only true for J2SE java.lang.Object.clone(), but might
                    // be different on other/newer java platforms
                    continue;
                }
                return true;
            }
            return false;
        }

        private PsiMethod[] getSuperMethodsInJavaSense(
                @NotNull PsiMethod method, @NotNull PsiClass methodClass) {
            final PsiMethod[] superMethods = method.findSuperMethods();
            final List<PsiMethod> toExclude = new SmartList<PsiMethod>();
            for (PsiMethod superMethod : superMethods) {
                final PsiClass superClass = superMethod.getContainingClass();
                if (!InheritanceUtil.isCorrectDescendant(methodClass,
                        superClass, true)) {
                    toExclude.add(superMethod);
                }
            }
            if (!toExclude.isEmpty()) {
                final List<PsiMethod> result =
                        new ArrayList<PsiMethod>(Arrays.asList(superMethods));
                result.removeAll(toExclude);
                return result.toArray(new PsiMethod[result.size()]);
            }
            return superMethods;
        }
    }
}
