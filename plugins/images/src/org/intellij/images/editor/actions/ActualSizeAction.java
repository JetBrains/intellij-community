package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.AbstractEditorAction;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Resize image to actual size.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 * @see ImageEditor#getZoomModel()
 * @see ImageZoomModel#setZoomFactor
 */
public final class ActualSizeAction extends AbstractEditorAction {
    public void actionPerformed(ImageEditor imageEditor, AnActionEvent e) {
        ImageZoomModel zoomModel = imageEditor.getZoomModel();
        ImageDocument document = imageEditor.getDocument();
        BufferedImage image = document.getValue();
        zoomModel.setSize(new Dimension(image.getWidth(), image.getHeight()));
        zoomModel.setZoomFactor(1.0d);
    }
}
