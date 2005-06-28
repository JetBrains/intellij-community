package org.intellij.images.editor;

import java.awt.*;

/**
 * Location model presents bounds of image.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageZoomModel {
    /**
     * Return zoom value of current image.
     */
    double getZoomFactor();

    /**
     * Zoom image.
     */
    void setZoomFactor(double zoomFactor);

    /**
     * Return size of curent visible image document. It not a actual size of image.
     */
    Dimension getSize();

    /**
     * Set visible size of image.
     */
    void setSize(Dimension size);
}
