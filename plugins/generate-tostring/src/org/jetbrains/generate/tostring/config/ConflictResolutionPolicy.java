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
 * Interface that defines a policy for dealing with conflicts (i.e., the user is
 * trying to tostring a {@link Object#toString} method but one already exists in
 * this class).
 */
public interface ConflictResolutionPolicy {

    /**
     * Inject the policy to use when inserting a new method.
     *
     * @param policy  the policy to use.
     * @since 3.18
     */
    void setInsertNewMethodPolicy(InsertNewMethodPolicy policy);

    /**
     * Applies the choosen policy.
     *
     * @param clazz          PSIClass.
     * @param existingMethod existing method if one exists.
     * @param newMethod      new method.
     * @return if the policy was executed normally (not cancelled)
     * @throws IncorrectOperationException is thrown if there is an IDEA error.
     */
    boolean applyMethod(PsiClass clazz, PsiMethod existingMethod, PsiMethod newMethod) throws IncorrectOperationException;

    /**
     * Applies the choose policy for javadoc.
     *
     * @param clazz              PSIClass
     * @param newMethod          New toString method
     * @param elementFactory     Element factory
     * @param codeStyleManager   CodeStyleManager
     * @param existingJavaDoc    Existing javadoc if any
     * @param newJavaDoc         The new javadoc if any
     * @return                   true if javadoc replace, false if left as it was before
     * @throws IncorrectOperationException is thrown if there is an IDEA error.
     * @since 2.20
     */
    boolean applyJavaDoc(PsiClass clazz, PsiMethod newMethod, PsiElementFactory elementFactory, CodeStyleManager codeStyleManager, String existingJavaDoc, String newJavaDoc) throws IncorrectOperationException;

}
