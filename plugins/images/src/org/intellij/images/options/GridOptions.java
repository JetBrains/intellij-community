package org.intellij.images.options;

import java.awt.*;

/**
 * Grid layer options
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface GridOptions extends Cloneable {
    int DEFAULT_LINE_ZOOM_FACTOR = 3;
    int DEFAULT_LINE_SPAN = 1;
    Color DEFAULT_LINE_COLOR = Color.DARK_GRAY;
    String ATTR_PREFIX = "Editor.Grid.";
    String ATTR_SHOW_DEFAULT = ATTR_PREFIX + "showDefault";
    String ATTR_LINE_ZOOM_FACTOR = ATTR_PREFIX + "lineZoomFactor";
    String ATTR_LINE_SPAN = ATTR_PREFIX + "lineSpan";
    String ATTR_LINE_COLOR = ATTR_PREFIX + "lineColor";

    boolean isShowDefault();

    int getLineZoomFactor();

    int getLineSpan();

    Color getLineColor();

    void inject(GridOptions options);

    boolean setOption(String name, Object value);
}
