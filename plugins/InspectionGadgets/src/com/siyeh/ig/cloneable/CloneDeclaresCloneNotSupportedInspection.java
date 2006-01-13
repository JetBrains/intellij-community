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
package com.siyeh.ig.cloneable;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class CloneDeclaresCloneNotSupportedInspection extends MethodInspection{
    private final CloneDeclaresCloneNotSupportedInspectionFix fix = new CloneDeclaresCloneNotSupportedInspectionFix();

    public String getID(){
        return "CloneDoesntDeclareCloneNotSupportedException";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("clone.doesnt.declare.clonenotsupportedexception.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("clone.doesnt.declare.clonenotsupportedexception.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class CloneDeclaresCloneNotSupportedInspectionFix
                                                                     extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("clone.doesnt.declare.clonenotsupportedexception.declare.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{

            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiMethod method = (PsiMethod) methodNameIdentifier.getParent();
            PsiUtil.addException(method,
                                 "java.lang.CloneNotSupportedException");
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new CloneDeclaresCloneNotSupportedExceptionVisitor();
    }

    private static class CloneDeclaresCloneNotSupportedExceptionVisitor
                                                                        extends BaseInspectionVisitor{
        public void visitMethod(@NotNull PsiMethod method){
            //note: no call to super;
            final String methodName = method.getName();
            if(!HardcodedMethodConstants.CLONE.equals(methodName)) {
              return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null){
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length != 0){
                return;
            }
            if(method.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            if(containingClass.hasModifierProperty(PsiModifier.FINAL)){
                return;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            if(throwsList == null){
                registerMethodError(method);
                return;
            }
            final PsiJavaCodeReferenceElement[] referenceElements = throwsList.getReferenceElements();
            for(final PsiJavaCodeReferenceElement referenceElement : referenceElements){
                final PsiElement referencedElement = referenceElement.resolve();
              if (referencedElement instanceof PsiClass) {
                final PsiClass aClass = (PsiClass)referencedElement;
                final String className = aClass.getQualifiedName();
                if ("java.lang.CloneNotSupportedException".equals(className)) {
                  return;
                }
              }
            }

            registerMethodError(method);
        }
    }
}
