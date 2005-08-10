/*
 * Copyright 2004-2005 Alexey Efimov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.images.options;

import java.awt.*;

/**
 * Background chessboard options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
@SuppressWarnings({"HardCodedStringLiteral"})
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
