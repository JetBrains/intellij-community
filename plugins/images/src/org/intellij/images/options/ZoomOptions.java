package org.intellij.images.options;

import java.awt.*;

/**
 * Options for zooming feature.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ZoomOptions extends Cloneable {
    Dimension DEFAULT_PREFFERED_SIZE = new Dimension(128, 128);
    String ATTR_PREFIX = "Editor.Zoom.";
    String ATTR_WHEEL_ZOOMING = ATTR_PREFIX + "wheelZooming";
    String ATTR_SMART_ZOOMING = ATTR_PREFIX + "smartZooming";
    String ATTR_PREFFERED_WIDTH = ATTR_PREFIX + "prefferedWidth";
    String ATTR_PREFFERED_HEIGHT = ATTR_PREFIX + "prefferedHeight";

    boolean isWheelZooming();

    boolean isSmartZooming();

    Dimension getPrefferedSize();

    void inject(ZoomOptions options);

    boolean setOption(String name, Object value);
}
