/*
 * @author max
 */
package org.jetbrains.generate.tostring.view;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.generate.tostring.template.TemplateResource;

import javax.swing.*;

public class ToStringTemplateConfigurable implements UnnamedConfigurable{
    private final TemplateResource template;
    private final Editor myEditor;

    public ToStringTemplateConfigurable(TemplateResource template) {
        this.template = template;
        final EditorFactory factory = EditorFactory.getInstance();
        final Document doc = factory.createDocument(template.getTemplate());
        myEditor = template.isDefault() ? factory.createViewer(doc) : factory.createEditor(doc);
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