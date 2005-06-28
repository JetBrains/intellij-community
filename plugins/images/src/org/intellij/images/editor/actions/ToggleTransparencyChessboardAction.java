package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.actionSystem.AbstractEditorToggleAction;

/**
 * Show/hide background action.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#setTransparencyChessboardVisible
 */
public final class ToggleTransparencyChessboardAction extends AbstractEditorToggleAction {
    public void setSelected(ImageEditor imageEditor, AnActionEvent e, boolean state) {
        imageEditor.setTransparencyChessboardVisible(state);
    }

    public boolean isSelected(ImageEditor imageEditor, AnActionEvent e) {
        return imageEditor.isTransparencyChessboardVisible();
    }
}
