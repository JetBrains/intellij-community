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
     *
     * @return Image renderer
     */
    Image getRenderer();

    /**
     * Return current image.
     *
     * @return Return current buffered image
     */
    BufferedImage getValue();

    /**
     * Set image value
     *
     * @param image Value
     */
    void setValue(BufferedImage image);

    /**
     * Return image format.
     *
     * @return Format name
     */
    String getFormat();

    /**
     * Set image format.
     *
     * @param format Format from ImageIO (GIF, PNG, JPEG etc)
     */
    void setFormat(String format);

    void addChangeListener(ChangeListener listener);

    void removeChangeListener(ChangeListener listener);
}
