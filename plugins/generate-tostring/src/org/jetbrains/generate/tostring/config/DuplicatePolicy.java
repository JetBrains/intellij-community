/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.generate.tostring.config;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;

/**
 * This policy is to create a duplicate <code>toString</code> method.
 */
public class DuplicatePolicy implements ConflictResolutionPolicy {

    private static final DuplicatePolicy instance = new DuplicatePolicy();
    private static InsertNewMethodPolicy newMethodPolicy;

    private DuplicatePolicy() {
    }

    public static DuplicatePolicy getInstance() {
        return instance;
    }

    public void setInsertNewMethodPolicy(InsertNewMethodPolicy policy) {
        newMethodPolicy = policy;
    }

    public boolean applyMethod(PsiClass clazz, PsiMethod existingMethod, PsiMethod newMethod) throws IncorrectOperationException {
        newMethodPolicy.insertNewMethod(clazz, newMethod);
        return true;
    }

    public boolean applyJavaDoc(PsiClass clazz, PsiMethod newMethod, PsiElementFactory elementFactory, CodeStyleManager codeStyleManager, String existingJavaDoc, String newJavaDoc) throws IncorrectOperationException {
        PsiAdapter psi = PsiAdapterFactory.getPsiAdapter();
        String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc

        if (psi.addOrReplaceJavadoc(elementFactory, codeStyleManager, newMethod, text, true) != null) {
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        return "Duplicate";
    }

}
