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

import org.jetbrains.annotations.NonNls;

import java.awt.*;

/**
 * Grid layer options
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface GridOptions extends Cloneable {
    @NonNls
    String ATTR_PREFIX = "Editor.Grid.";
    @NonNls
    String ATTR_SHOW_DEFAULT = ATTR_PREFIX + "showDefault";
    @NonNls
    String ATTR_LINE_ZOOM_FACTOR = ATTR_PREFIX + "lineZoomFactor";
    @NonNls
    String ATTR_LINE_SPAN = ATTR_PREFIX + "lineSpan";
    @NonNls
    String ATTR_LINE_COLOR = ATTR_PREFIX + "lineColor";

    int DEFAULT_LINE_ZOOM_FACTOR = 3;
    int DEFAULT_LINE_SPAN = 1;
    Color DEFAULT_LINE_COLOR = Color.DARK_GRAY;

    boolean isShowDefault();

    int getLineZoomFactor();

    int getLineSpan();

    Color getLineColor();

    void inject(GridOptions options);

    boolean setOption(String name, Object value);
}
