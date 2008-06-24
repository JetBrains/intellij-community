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
 * This policy is to replace the existing <code>toString</code> method.
 */
public class ReplacePolicy implements ConflictResolutionPolicy {

    private static final ReplacePolicy instance = new ReplacePolicy();
    private static PsiAdapter psi;

    private ReplacePolicy() {
    }

    public static ReplacePolicy getInstance() {
        return instance;
    }

    public void setInsertNewMethodPolicy(InsertNewMethodPolicy policy) {
        // not needed here
    }

    public boolean applyMethod(PsiClass clazz, PsiMethod existingMethod, PsiMethod newMethod) throws IncorrectOperationException {
        if (existingMethod != null) {
            existingMethod.replace(newMethod);
            return true;
        } else {
            return DuplicatePolicy.getInstance().applyMethod(clazz, existingMethod, newMethod);
        }
    }

    public boolean applyJavaDoc(PsiClass clazz, PsiMethod newMethod, PsiElementFactory elementFactory, CodeStyleManager codeStyleManager, String existingJavaDoc, String newJavaDoc) throws IncorrectOperationException {
        // lazy initialize otherwise IDEA throws error: Component requests are not allowed before they are created
        if (psi == null) {
            psi = PsiAdapterFactory.getPsiAdapter();
        }

        String text = newJavaDoc != null ? newJavaDoc : existingJavaDoc; // prefer to use new javadoc

        if (psi.addOrReplaceJavadoc(elementFactory, codeStyleManager, newMethod, text, true) != null) {
            return true;
        } else
            return false;
    }

    public String toString() {
        return "Replace existing";
    }

}
