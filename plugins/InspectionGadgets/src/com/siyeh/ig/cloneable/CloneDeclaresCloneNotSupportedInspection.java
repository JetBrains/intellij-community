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
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.NotNull;

public class CloneDeclaresCloneNotSupportedInspection extends BaseInspection {

    @NotNull
    public String getID(){
        return "CloneDoesntDeclareCloneNotSupportedException";
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "clone.doesnt.declare.clonenotsupportedexception.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "clone.doesnt.declare.clonenotsupportedexception.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return new CloneDeclaresCloneNotSupportedInspectionFix();
    }

    private static class CloneDeclaresCloneNotSupportedInspectionFix
            extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "clone.doesnt.declare.clonenotsupportedexception.declare.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiMethod method =
                    (PsiMethod) methodNameIdentifier.getParent();
            PsiUtil.addException(method,
                                 "java.lang.CloneNotSupportedException");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CloneDeclaresCloneNotSupportedExceptionVisitor();
    }

    private static class CloneDeclaresCloneNotSupportedExceptionVisitor
            extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            if(!CloneUtils.isClone(method)){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            if(hasThrowsCloneNotSupportedException(method)){
                return;
            }
            final PsiMethod[] superMethods = method.findSuperMethods();
            if (superMethods.length < 1){
                return;
            }
            if(!hasThrowsCloneNotSupportedException(superMethods[0])){
                return;
            }
            registerMethodError(method);
        }

        public static boolean hasThrowsCloneNotSupportedException(
                @NotNull PsiMethod method){
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiClassType[] thrownTypes = throwsList.getReferencedTypes();
            for(final PsiClassType thrownType : thrownTypes){
                if (thrownType.equalsToText(
                        "java.lang.CloneNotSupportedException")){
                    return true;
                }
            }
            return false;
        }
    }
}