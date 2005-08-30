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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class ClassReferencesSubclassInspection extends ClassInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("class.references.subclass.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        final PsiClass containingClass =
                ClassUtils.getContainingClass(location);
        assert containingClass != null;
        final String containingClassName = containingClass.getName();
        return InspectionGadgetsBundle.message("class.references.subclass.problem.descriptor", containingClassName);
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ClassReferencesSubclassVisitor();
    }

    private static class ClassReferencesSubclassVisitor extends BaseInspectionVisitor{
        private boolean m_inClass = false;
        private PsiClass containingClass = null;

        public void visitClass(@NotNull PsiClass aClass){
            final boolean wasInClass = m_inClass;
            if(!m_inClass){

                m_inClass = true;
                containingClass = aClass;
                super.visitClass(aClass);
                containingClass = null;
            }
            m_inClass = wasInClass;
        }

        public void visitVariable(@NotNull PsiVariable variable){
            final PsiTypeElement typeElement = variable.getTypeElement();
            checkTypeElement(typeElement);
        }


        public void visitMethod(@NotNull PsiMethod method){
            super.visitMethod(method);
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            checkTypeElement(typeElement);
        }

        public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression exp){
            super.visitInstanceOfExpression(exp);
            final PsiTypeElement typeElement = exp.getCheckType();
            checkTypeElement(typeElement);
        }


        public void visitTypeCastExpression(@NotNull PsiTypeCastExpression exp){
            super.visitTypeCastExpression(exp);
            final PsiTypeElement typeElement = exp.getCastType();
            checkTypeElement(typeElement);
        }

        public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression exp){
            super.visitClassObjectAccessExpression(exp);
            final PsiTypeElement typeElement = exp.getOperand();
            checkTypeElement(typeElement);
        }

        private void checkTypeElement(PsiTypeElement typeElement){
            if(typeElement == null){
                return;
            }
            final PsiType type = typeElement.getType();
            if(type == null){
                return;
            }
            final PsiType componentType = type.getDeepComponentType();
            if(!(componentType instanceof PsiClassType)){
                return;
            }
            final PsiClassType classType = (PsiClassType) componentType;
            if(!isSubclass(classType, containingClass)){
                return;
            }
            registerError(typeElement);
        }

        private static boolean isSubclass(PsiClassType childClass,
                                          PsiClass parent){
            final PsiClass child = childClass.resolve();
            if(child == null){
                return false;
            }
            return child.isInheritor(parent, true);
        }
    }
}
