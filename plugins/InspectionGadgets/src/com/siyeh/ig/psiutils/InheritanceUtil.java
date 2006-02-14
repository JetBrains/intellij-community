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
        if(class1.isInheritor(class2, true) ||
                class2.isInheritor(class1, true)){
            return true;
        }
        final MutualSubclassProcessor processor =
                new MutualSubclassProcessor(class1, class2);
        return processor.hasMutualSubclass();
    }

    public static boolean hasImplementation(PsiClass aClass) {
        final ConcreteClassProcessor concreteClassProcessor =
                new ConcreteClassProcessor(aClass);
        return concreteClassProcessor.hasImplementation();
    }

    private static class MutualSubclassProcessor
            implements PsiElementProcessor<PsiClass>, Runnable {

        private final PsiClass class1;
        private final PsiClass class2;
        private boolean mutualSubClass = false;

        MutualSubclassProcessor(PsiClass class1, PsiClass class2) {
            this.class1 = class1;
            this.class2 = class2;
        }

        public boolean execute(PsiClass inheritor) {
            if (inheritor.equals(class2) ||
                    inheritor.isInheritor(class2, true)) {
                mutualSubClass = true;
                return false;
            }
            return true;
        }

        public boolean hasMutualSubclass() {
            final ProgressManager progressManager =
                    ProgressManager.getInstance();
            progressManager.runProcess(this, null);
            return mutualSubClass;
        }

        public void run() {
            final PsiManager psiManager = class1.getManager();
            final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
            final SearchScope searchScope = class1.getUseScope();
            searchHelper.processInheritors(this, class1, searchScope, true);
        }
    }

    private static class ConcreteClassProcessor
            implements PsiElementProcessor<PsiClass>, Runnable {

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
            final ProgressManager progressManager =
                    ProgressManager.getInstance();
            progressManager.runProcess(this, null);
            return implementation;
        }

        public void run() {
            final PsiManager manager = aClass.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final SearchScope searchScope = aClass.getUseScope();
            searchHelper.processInheritors(this, aClass, searchScope, true);
        }
    }
}