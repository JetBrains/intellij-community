/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package org.jetbrains.java.generate.view;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.java.generate.template.TemplateResource;

import javax.swing.*;

public class GenerateTemplateConfigurable implements UnnamedConfigurable{
    private final TemplateResource template;
    private final Editor myEditor;

    public GenerateTemplateConfigurable(TemplateResource template, Project project) {
        this.template = template;
        final EditorFactory factory = EditorFactory.getInstance();
        final Document doc = factory.createDocument(template.getTemplate());
        final FileType ftl = FileTypeManager.getInstance().findFileTypeByName("VTL");
        myEditor = factory.createEditor(doc, project, ftl != null ? ftl : FileTypes.PLAIN_TEXT, template.isDefault());
    }

    public JComponent createComponent() {
        return myEditor.getComponent();
    }

    public boolean isModified() {
        return !Comparing.equal(myEditor.getDocument().getText(), template.getTemplate());
    }

    public void apply() throws ConfigurationException {
        template.setTemplate(myEditor.getDocument().getText());
    }

    public void reset() {
        new WriteCommandAction(null) {
            protected void run(Result result) throws Throwable {
                myEditor.getDocument().setText(template.getTemplate());
            }
        }.execute();
    }

    public void disposeUIResources() {
        EditorFactory.getInstance().releaseEditor(myEditor);
    }
}