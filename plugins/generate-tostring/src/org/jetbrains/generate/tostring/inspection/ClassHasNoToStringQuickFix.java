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
package org.jetbrains.generate.tostring.inspection;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.generate.tostring.GenerateToStringActionHandler;
import org.jetbrains.generate.tostring.GenerateToStringActionHandlerImpl;
import org.jetbrains.generate.tostring.GenerateToStringContext;
import org.jetbrains.generate.tostring.config.InsertAtCaretPolicy;
import org.jetbrains.generate.tostring.config.InsertLastPolicy;
import org.jetbrains.generate.tostring.config.InsertNewMethodPolicy;
import org.jetbrains.annotations.NotNull;

/**
 * Quick fix to run Generate toString() to fix any code inspection problems.
 */
public class ClassHasNoToStringQuickFix extends AbstractGenerateToStringQuickFix {

    public void applyFix(@NotNull Project project, ProblemDescriptor desc) {

        // find the class
        PsiClass clazz = psi.findClass(desc.getPsiElement());
        if (clazz == null) {
            return; // no class to fix, so return
        }

        // execute the action
        GenerateToStringActionHandler handler = new GenerateToStringActionHandlerImpl();

        // determine what insert policy to use
        InsertNewMethodPolicy policy = GenerateToStringContext.getConfig().getInsertNewMethodInitialOption();
        if (policy instanceof InsertAtCaretPolicy) {
            // okay if the config is set to insert at caret, we should override this to insert
            // last instead, otherwise the javacode will be inserted in the middle of the classname declaration
            policy = InsertLastPolicy.getInstance();
        }

        // must use insert last policy otherwise we might insert a new method where the cursor is position and
        // that position could be in the javafile class name where the inspection is highlighted for (ClassHasNoToStringInsepction)
        handler.executeActionQickFix(project, clazz, desc, policy);
    }

}
