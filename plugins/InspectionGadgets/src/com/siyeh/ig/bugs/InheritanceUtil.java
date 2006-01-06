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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;

class InheritanceUtil{

    private InheritanceUtil(){
        super();
    }

    public static boolean existsMutualSubclass(PsiClass class1,
                                               PsiClass class2){
        final String className = class1.getQualifiedName();
        if("java.lang.Object".equals(className)){
            return true;
        }
        final String class2Name = class2.getQualifiedName();
        if("java.lang.Object".equals(class2Name)){
            return true;
        }
        if(class1.isInheritor(class2, true) || class2.isInheritor(class1, true)){
            return true;
        }
        final PsiManager psiManager = class1.getManager();
        final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
        final SearchScope searchScope = class1.getUseScope();
        final PsiClass[] inheritors =
                searchHelper.findInheritors(class1, searchScope, true);
        for(final PsiClass inheritor : inheritors){
            if(inheritor.equals(class2) ||
                    inheritor.isInheritor(class2, true)){
                return true;
            }
        }
        return false;
    }
}