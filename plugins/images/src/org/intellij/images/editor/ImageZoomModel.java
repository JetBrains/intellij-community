package org.intellij.images.editor;

/**
 * Location model presents bounds of image.
 * The zoom it calculated as y = exp(x/2).
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageZoomModel {
    int MACRO_ZOOM_LIMIT = 32;
    int MICRO_ZOOM_LIMIT = 8;

    /**
     * Return zoom value of current image.
     */
    double getZoomFactor();

    /**
     * Zoom image.
     */
    void setZoomFactor(double zoomFactor);

    void zoomOut();

    void zoomIn();

    boolean canZoomOut();

    boolean canZoomIn();
}
