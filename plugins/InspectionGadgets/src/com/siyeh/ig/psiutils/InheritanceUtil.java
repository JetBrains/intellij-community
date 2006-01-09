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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.openapi.progress.ProgressManager;

public class InheritanceUtil{

    private InheritanceUtil(){
        super();
    }

    public static boolean existsMutualSubclass(final PsiClass class1,
                                               PsiClass class2){
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
        final PsiManager psiManager = class1.getManager();
        final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
        final SearchScope searchScope = class1.getUseScope();
        final MutualSubclassProcessor processor =
                new MutualSubclassProcessor(class2);
        final ProgressManager progressManager = ProgressManager.getInstance();
        progressManager.runProcess(new Runnable() {
            public void run() {
                searchHelper.processInheritors(processor, class1,
                        searchScope, true);
            }
        }, null);
        return processor.hasMutualSubclass();
    }

    public static boolean hasImplementation(PsiClass aClass) {
        final ConcreteClassProcessor concreteClassProcessor =
                new ConcreteClassProcessor(aClass);
        return concreteClassProcessor.hasImplementation();
    }

    private static class MutualSubclassProcessor
            implements PsiElementProcessor<PsiClass> {

        private boolean mutualSubClass = false;
        private PsiClass aClass;

        MutualSubclassProcessor(PsiClass aClass) {
            this.aClass = aClass;
        }

        public boolean execute(PsiClass inheritor) {
            if (inheritor.equals(aClass) ||
                inheritor.isInheritor(aClass, true)) {
                mutualSubClass = true;
                return false;
            }
            return true;
        }

        public boolean hasMutualSubclass() {
            return mutualSubClass;
        }
    }

    private static class ConcreteClassProcessor
            implements PsiElementProcessor<PsiClass> {

        private final PsiClass aClass;
        private boolean implementation = false;

        ConcreteClassProcessor(PsiClass aClass) {
            this.aClass = aClass;
        }

        public boolean execute(PsiClass inheritor) {
            if (!(inheritor.isInterface() || inheritor.isAnnotationType() ||
                  inheritor.hasModifierProperty(PsiModifier.ABSTRACT))) {
                implementation = true;
                return false;
            }
            return true;
        }

        public boolean hasImplementation() {
            final PsiManager manager = aClass.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final SearchScope searchScope = aClass.getUseScope();
            searchHelper.processInheritors(this, aClass, searchScope, true);
            return implementation;
        }
    }
}