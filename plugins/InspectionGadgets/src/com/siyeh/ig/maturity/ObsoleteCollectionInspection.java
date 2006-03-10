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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import com.siyeh.ig.psiutils.LibraryUtil;
import org.jetbrains.annotations.NotNull;

public class ObsoleteCollectionInspection extends VariableInspection{

    public String getID(){
        return "UseOfObsoleteCollectionType";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "use.obsolete.collection.type.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.MATURITY_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "use.obsolete.collection.type.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ObsoleteCollectionVisitor();
    }

    private static class ObsoleteCollectionVisitor
            extends BaseInspectionVisitor{

        public void visitVariable(@NotNull PsiVariable variable){
            super.visitVariable(variable);
            final PsiType type = variable.getType();
            if (!isObsoleteCollectionType(type)){
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            registerError(typeElement);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            final PsiType returnType = method.getReturnType();
            if (!isObsoleteCollectionType(returnType)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            registerError(typeElement);
        }

        public void visitNewExpression(@NotNull PsiNewExpression newExpression){
            super.visitNewExpression(newExpression);
            final PsiType type = newExpression.getType();
            if (!isObsoleteCollectionType(type)){
                return;
            }
            final PsiJavaCodeReferenceElement classNameElement =
                    newExpression.getClassReference();
            registerError(classNameElement);
        }

        @SuppressWarnings({"HardCodedStringLiteral"})
        private static boolean isObsoleteCollectionType(PsiType type) {
            if(type == null){
                return false;
            }
            final PsiType deepComponentType = type.getDeepComponentType();
            if (!(deepComponentType instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType)deepComponentType;
            final String className = classType.getClassName();
            if (!"Vector".equals(className) && !"Hashtable".equals(className)) {
                return false;
            }
            final PsiClass aClass = classType.resolve();
            if (aClass == null) {
                return false;
            }
            final String name = aClass.getQualifiedName();
            return "java.util.Vector".equals(name) ||
                   "java.util.Hashtable".equals(name);
        }
    }
}
