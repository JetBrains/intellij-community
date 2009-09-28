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
 * Options for zooming feature.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ZoomOptions extends Cloneable {
    @NonNls
    String ATTR_PREFIX = "Editor.Zoom.";
    @NonNls
    String ATTR_WHEEL_ZOOMING = ATTR_PREFIX + "wheelZooming";
    @NonNls
    String ATTR_SMART_ZOOMING = ATTR_PREFIX + "smartZooming";
    @NonNls
    String ATTR_PREFFERED_WIDTH = ATTR_PREFIX + "prefferedWidth";
    @NonNls
    String ATTR_PREFFERED_HEIGHT = ATTR_PREFIX + "prefferedHeight";

    Dimension DEFAULT_PREFFERED_SIZE = new Dimension(128, 128);

    boolean isWheelZooming();

    boolean isSmartZooming();

    Dimension getPrefferedSize();

    void inject(ZoomOptions options);

    boolean setOption(String name, Object value);
}
