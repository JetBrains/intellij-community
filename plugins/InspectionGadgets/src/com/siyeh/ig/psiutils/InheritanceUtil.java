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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;

public class InheritanceUtil{

    private InheritanceUtil(){
        super();
    }

    public static boolean existsMutualSubclass(PsiClass class1,
                                               PsiClass class2){
        if (class1 instanceof PsiTypeParameter) {
            final PsiClass[] superClasses = class1.getSupers();
            for (PsiClass superClass : superClasses) {
                if (!existsMutualSubclass(superClass, class2)) {
                    return false;
                }
            }
            return true;
        } else if (class2 instanceof PsiTypeParameter) {
            return existsMutualSubclass(class2, class1);
        }

        final String className = class1.getQualifiedName();
        if("java.lang.Object".equals(className)){
            return true;
        }
        final String class2Name = class2.getQualifiedName();
        if("java.lang.Object".equals(class2Name)){
            return true;
        }
        if(class1.isInheritor(class2, true) ||
                class2.isInheritor(class1, true)){
            return true;
        }
        final SearchScope scope =
                GlobalSearchScope.projectScope(class1.getProject());
        final Query<PsiClass> search =
                ClassInheritorsSearch.search(class1, scope, true, true);
        for (PsiClass inheritor : search) {
            if (inheritor.equals(class2) ||
                    inheritor.isInheritor(class2, true)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasImplementation(PsiClass aClass) {
        final SearchScope scope =
                GlobalSearchScope.projectScope(aClass.getProject());
        final Query<PsiClass> search =
                ClassInheritorsSearch.search(aClass, scope, true, true);
        for (PsiClass inheritor : search) {
            if (!(inheritor.isInterface() || inheritor.isAnnotationType() ||
                    inheritor.hasModifierProperty(PsiModifier.ABSTRACT))) {
                return true;
            }
        }
        return false;
    }
}