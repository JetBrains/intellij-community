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
package com.siyeh.ig.maturity;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

public class SystemOutErrInspection extends ExpressionInspection {
    public String getID(){
        return "UseOfSystemOutOrSystemErr";
    }
    public String getDisplayName() {
        return InspectionGadgetsBundle.message("use.system.out.err.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.MATURITY_GROUP_NAME;
    }



    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("use.system.out.err.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new SystemOutErrVisitor();
    }

    private static class SystemOutErrVisitor extends BaseInspectionVisitor {

        public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
            super.visitReferenceExpression(expression);

            final String name = expression.getReferenceName();
            if (!HardcodedMethodConstants.OUT.equals(name) && !HardcodedMethodConstants.ERR.equals(name)) {
                return;
            }
            final PsiElement referent = expression.resolve();
            if(!(referent instanceof PsiField))
            {
               return;
            }
            final PsiClass containingClass = ((PsiMember) referent).getContainingClass();
            if(containingClass == null)
            {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if(!"java.lang.System".equals(className))
            {
                return;
            }
            registerError(expression);
        }

    }

}
