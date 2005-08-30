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
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class CloneCallsSuperCloneInspection extends MethodInspection {
    public String getID(){
        return "CloneDoesntCallSuperClone";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("clone.doesnt.call.super.clone.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("clone.doesnt.call.super.clone.problem.descriptor");
    }

    public boolean isEnabledByDefault(){
        return true;
    }
    public BaseInspectionVisitor buildVisitor() {
        return new NoExplicitCloneCallsVisitor();
    }

    private static class NoExplicitCloneCallsVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            //note: no call to super;
            final String methodName = method.getName();
            if (!HardcodedMethodConstants.CLONE.equals(methodName)) {
              return;
            }
            if(method.hasModifierProperty(PsiModifier.ABSTRACT) ||
                    method.hasModifierProperty(PsiModifier.NATIVE))
            {
                return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParameters().length != 0) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            if (containingClass.isInterface() || containingClass.isAnnotationType()) {
                return;
            }
            final CallToSuperCloneVisitor visitor = new CallToSuperCloneVisitor();
            method.accept(visitor);
            if (visitor.isCallToSuperCloneFound()) {
                return;
            }
            registerMethodError(method);
        }

    }

}
