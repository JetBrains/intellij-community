package org.intellij.images.editor;

import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Image document to show or edit in {@link ImageEditor}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ImageDocument {
    /**
     * Return image for rendering
     */
    Image getRenderer();

    /**
     * Return current image.
     */
    BufferedImage getValue();

    /**
     * Set image value
     * @param image Value
     */
    void setValue(BufferedImage image);

    void addChangeListener(ChangeListener listener);

    void removeChangeListener(ChangeListener listener);

}
