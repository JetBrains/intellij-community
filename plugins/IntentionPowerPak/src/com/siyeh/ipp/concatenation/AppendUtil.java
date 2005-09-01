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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.NonNls;

class AppendUtil{
    private AppendUtil(){
        super();
    }

    public static boolean isAppend(PsiMethodCallExpression call){
        final PsiReferenceExpression methodExpression =
                call.getMethodExpression();
        if(methodExpression == null){
            return false;
        }
        @NonNls final String callName = methodExpression.getReferenceName();
        if(!"append".equals(callName)){
            return false;
        }
        final PsiMethod method = call.resolveMethod();
        if(method == null){
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if(containingClass == null){
            return false;
        }
        final String name = containingClass.getQualifiedName();
        return "java.lang.StringBuffer".equals(name) ||
               "java.lang.StringBuilder".equals(name);
    }
}
