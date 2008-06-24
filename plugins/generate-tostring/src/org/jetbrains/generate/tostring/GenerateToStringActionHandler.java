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
package org.jetbrains.generate.tostring;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import org.jetbrains.generate.tostring.config.InsertNewMethodPolicy;
import org.jetbrains.generate.tostring.template.TemplateResource;

/**
 * Main interface for the plugin.
 * <p/>
 * This handler is the entry point to execute the action from different situations.
 */
public interface GenerateToStringActionHandler {

    /**
     * The action that does the actual generation of the code.
     * <p/>
     * This is called automatically by IDEA when user invokes the plugin from the generate menu.
     *
     * @param editor      the current editor.
     * @param dataContext the current data context.
     */
    void executeWriteAction(Editor editor, DataContext dataContext);

    /**
     * Action to be executed from the template quick selection dialog.
     *
     * @param project           the current project.
     * @param clazz             the class.
     * @param quickTemplate     the selected quick template
     * @param insertPolicy      overrule to use this policy (usually by quickfix), null to use default
     */
    void executeActionTemplateQuickSelection(Project project, PsiClass clazz, TemplateResource quickTemplate, InsertNewMethodPolicy insertPolicy);


    /**
     * Action to be executed from quick fix.
     *
     * @param project           the current project.
     * @param clazz             the class.
     * @param desc              the problem found by that quick fix should solve
     * @param insertPolicy      overrule to use this policy (usually by quickfix), null to use default
     */
    void executeActionQickFix(Project project, PsiClass clazz, ProblemDescriptor desc, InsertNewMethodPolicy insertPolicy);

}
