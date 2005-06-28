package org.intellij.images.options;

import java.awt.*;

/**
 * Background chessboard options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface TransparencyChessboardOptions extends Cloneable {
    int DEFAULT_CELL_SIZE = 5;
    Color DEFAULT_WHITE_COLOR = Color.WHITE;
    Color DEFAULT_BLACK_COLOR = Color.LIGHT_GRAY;
    String ATTR_PREFIX = "Editor.TransparencyChessboard.";
    String ATTR_SHOW_DEFAULT = ATTR_PREFIX + "showDefault";
    String ATTR_CELL_SIZE = ATTR_PREFIX + "cellSize";
    String ATTR_WHITE_COLOR = ATTR_PREFIX + "whiteColor";
    String ATTR_BLACK_COLOR = ATTR_PREFIX + "blackColor";

    boolean isShowDefault();

    int getCellSize();

    Color getWhiteColor();

    Color getBlackColor();

    void inject(TransparencyChessboardOptions options);

    boolean setOption(String name, Object value);
}
