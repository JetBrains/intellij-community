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

/**
 * This policy is to cancel.
 */
public class CancelPolicy implements ConflictResolutionPolicy {

    private static final CancelPolicy instance = new CancelPolicy();

    private CancelPolicy() {
    }

    public static CancelPolicy getInstance() {
        return instance;
    }

    public void setInsertNewMethodPolicy(InsertNewMethodPolicy policy) {
        // not used as this is cancel
    }

    public boolean applyMethod(PsiClass clazz, PsiMethod existingMethod, PsiMethod newMethod) throws IncorrectOperationException {
        return false; // the user cancels
    }

    public boolean applyJavaDoc(PsiClass clazz, PsiMethod newMethod, PsiElementFactory elementFactory, CodeStyleManager codeStyleManager, String existingJavaDoc, String newJavaDoc) throws IncorrectOperationException {
        return false;  // the user cancels
    }

    public String toString() {
        return "Cancel";
    }


}
