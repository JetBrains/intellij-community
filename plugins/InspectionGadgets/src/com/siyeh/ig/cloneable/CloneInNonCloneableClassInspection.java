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
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.MakeCloneableFix;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class CloneInNonCloneableClassInspection extends MethodInspection {

    private InspectionGadgetsFix fix = new MakeCloneableFix();
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.class.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.CLONEABLE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("clone.method.in.non.cloneable.class.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CloneInNonCloneableClassVisitor();
    }

    private static class CloneInNonCloneableClassVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method){
            final PsiClass containingClass = method.getContainingClass();
            final String name = method.getName();
            if(!HardcodedMethodConstants.CLONE.equals(name)) {
              return;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterList == null)
            {
                return;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if(parameters == null || parameters.length!=0)
            {
                return;
            }
            if(containingClass == null)
            {
                return;
            }
            if(CloneUtils.isCloneable(containingClass)){
                return;
            }
            registerMethodError(method);
        }

    }

}
