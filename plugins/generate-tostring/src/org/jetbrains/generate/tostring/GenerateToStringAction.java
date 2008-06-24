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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.generate.tostring.psi.PsiAdapter;
import org.jetbrains.generate.tostring.psi.PsiAdapterFactory;

/**
 * The IDEA action for this plugin.
 * <p/>
 * This action handles the generation of a <code>toString()</code> method that dumps the fields
 * of the class.
 */
public class GenerateToStringAction extends EditorAction {

    /**
     * Constructor.
     */
    public GenerateToStringAction() {
        super(new GenerateToStringActionHandlerImpl()); // register our action handler
    }


    /**
     * Updates the presentation of this action. Will disable this action for non-java files.
     *
     * @param editor       IDEA editor.
     * @param presentation Presentation.
     * @param dataContext  data context.
     */
    public void update(Editor editor, Presentation presentation, DataContext dataContext) {
        PsiAdapter psi = PsiAdapterFactory.getPsiAdapter();
        Project project = editor.getProject();
        PsiManager manager = psi.getPsiManager(project);

        PsiJavaFile javaFile = psi.getSelectedJavaFile(project, manager);
        if (javaFile == null) {
            presentation.setEnabled(false);
            return;
        }

        PsiClass clazz = psi.getCurrentClass(javaFile, editor);
        if (clazz == null) {
            presentation.setEnabled(false);
            return;
        }

        // must not be an interface
        presentation.setEnabled(! clazz.isInterface());
    }

    protected GenerateToStringAction(EditorActionHandler editorActionHandler) {
    super(editorActionHandler);    //To change body of overridden methods use File | Settings | File Templates.
}
}