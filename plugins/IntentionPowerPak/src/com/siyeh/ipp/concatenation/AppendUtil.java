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
package com.siyeh.ipp.concatenation;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

class AppendUtil{

	private AppendUtil(){
        super();
    }

    public static boolean isAppendCall(PsiElement element){
	    if (!(element instanceof PsiMethodCallExpression)) {
		    return false;
	    }
	    final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
	    final PsiReferenceExpression methodExpression =
			    methodCallExpression.getMethodExpression();
	    @NonNls final String callName = methodExpression.getReferenceName();
        if(!"append".equals(callName)){
            return false;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if(method == null){
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if(containingClass == null){
            return false;
        }
        final String name = containingClass.getQualifiedName();
	    if ("java.lang.StringBuffer".equals(name) ||
	        "java.lang.StringBuilder".equals(name)) {
		    return true;
	    }
	    final PsiManager manager = containingClass.getManager();
	    final Project project = containingClass.getProject();
	    final PsiClass appendableClass =
			    manager.findClass("java.lang.Appendable", GlobalSearchScope.allScope(project));
	    if (appendableClass == null) {
		    return false;
	    }
	    return containingClass.isInheritor(appendableClass, true);
    }
}
