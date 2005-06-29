package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.AbstractEditorAction;

/**
 * Zoom out.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#getZoomModel
 */
public final class ZoomOutAction extends AbstractEditorAction {
    public void actionPerformed(ImageEditor imageEditor, AnActionEvent e) {
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        zoomModel.zoomOut();
    }

    public void update(ImageEditor imageEditor, AnActionEvent e) {
        super.update(imageEditor, e);
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(zoomModel.canZoomOut());
    }
}
