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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class IteratorHasNextCallsIteratorNextInspection
        extends MethodInspection{

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "iterator.hasnext.which.calls.next.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "iterator.hasnext.which.calls.next.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IteratorHasNextCallsIteratorNext();
    }

    private static class IteratorHasNextCallsIteratorNext
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            // note: no call to super
            @NonNls final String name = method.getName();
            if(!HardcodedMethodConstants.HAS_NEXT.equals(name)){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            if(parameters.length != 0){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(!IteratorUtils.isIterator(aClass)){
                return;
            }
            if(!IteratorUtils.callsIteratorNext(method, true)){
                return;
            }
            registerMethodError(method);
        }
    }
}