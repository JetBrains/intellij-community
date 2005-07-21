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
package org.intellij.images.options.impl;

import com.intellij.openapi.util.JDOMExternalizer;
import org.jdom.Element;

import java.awt.*;

/**
 * Extension for {@link JDOMExternalizer}.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class JDOMExternalizerEx {
    public static Color readColor(Element root, String name, Color defaultValue) {
        String colorValue = JDOMExternalizer.readString(root, name);
        if (colorValue != null) {
            try {
                return new Color(Integer.parseInt(colorValue, 16));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return defaultValue;
    }

    public static void write(Element root, String name, Color value) {
        if (value != null) {
            JDOMExternalizer.write(root, name, Integer.toString(value.getRGB() & 0xFFFFFF, 16));
        }
    }
}
