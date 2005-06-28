package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.AbstractEditorAction;
import org.intellij.images.options.ZoomOptions;

/**
 * Zoom in.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#getZoomModel
 * @see ImageZoomModel#setZoomFactor
 */
public final class ZoomInAction extends AbstractEditorAction {
    public void actionPerformed(ImageEditor imageEditor, AnActionEvent e) {
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        zoomModel.setZoomFactor(zoomModel.getZoomFactor() * 2.0d);
    }

    public void update(ImageEditor imageEditor, AnActionEvent e) {
        super.update(imageEditor, e);
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        double zoomFactor = zoomModel.getZoomFactor();
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(zoomFactor < ZoomOptions.MAX_ZOOM_FACTOR);
    }
}
