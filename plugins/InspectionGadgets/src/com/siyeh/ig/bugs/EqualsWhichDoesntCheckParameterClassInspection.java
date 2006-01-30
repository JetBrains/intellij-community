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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsWhichDoesntCheckParameterClassInspection
        extends MethodInspection{
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new EqualsWhichDoesntCheckParameterClassVisitor();
    }

    private static class EqualsWhichDoesntCheckParameterClassVisitor
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
              // note: no call to super
              final String name = method.getName();
              if(!HardcodedMethodConstants.EQUALS.equals(name)){
                  return;
              }
              if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                  return;
              }
              final PsiParameterList paramList = method.getParameterList();
              if(paramList == null){
                  return;
              }
              final PsiParameter[] parameters = paramList.getParameters();
              if(parameters.length != 1){
                  return;
              }
              final PsiParameter parameter = parameters[0];
              final PsiType argType = parameter.getType();
              if(!TypeUtils.isJavaLangObject(argType)){
                  return;
              }
              final PsiCodeBlock body = method.getBody();
              if(body == null){
                  return;
              }
              if(isParameterChecked(body, parameter)){
                  return;
              }
              registerMethodError(method);
          }

        private static boolean isParameterChecked(PsiCodeBlock body,
                                                  PsiParameter parameter){
            final ParameterClassCheckVisitor visitor =
                    new ParameterClassCheckVisitor(parameter);
            body.accept(visitor);
            return visitor.isChecked();
        }
    }

}
