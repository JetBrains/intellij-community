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
