package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.actionSystem.AbstractEditorToggleAction;

/**
 * Toggle grid lines over image.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#setGridVisible
 */
public final class ToggleGridAction extends AbstractEditorToggleAction {
    public void setSelected(ImageEditor imageEditor, AnActionEvent e, boolean state) {
        imageEditor.setGridVisible(state);
    }

    public boolean isSelected(ImageEditor imageEditor, AnActionEvent e) {
        return imageEditor.isGridVisible();
    }
}
