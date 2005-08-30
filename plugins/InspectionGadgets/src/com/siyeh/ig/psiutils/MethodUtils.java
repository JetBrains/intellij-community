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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.siyeh.HardcodedMethodConstants;

public class MethodUtils{
    private MethodUtils(){
        super();
    }

    public static boolean isCompareTo(PsiMethod method){
        return methodMatches(method, HardcodedMethodConstants.COMPARE_TO, 1, PsiType.INT);
    }
    public static boolean isHashCode(PsiMethod method){
        return methodMatches(method, HardcodedMethodConstants.HASH_CODE, 0, PsiType.INT);
    }

    public static boolean isEquals(PsiMethod method){
        return methodMatches(method, HardcodedMethodConstants.EQUALS, 1, PsiType.BOOLEAN);
    }

    private static boolean methodMatches(PsiMethod method,
                                         String methodNameP,
                                         int parameterCount,
                                         PsiType returnTypeP){
        if(method == null){
            return false;
        }
        final String methodName = method.getName();
        if(!methodNameP.equals(methodName)){
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if(parameterList == null){
            return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        if(parameters.length != parameterCount){
            return false;
        }
        final PsiType returnType = method.getReturnType();
        return returnTypeP.equals(returnType);
    }

}
