/** $Id$ */
package org.intellij.images.options;

/**
 * External editor options.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface ExternalEditorOptions extends Cloneable {
    String ATTR_PREFIX = "ExternalEditor.";
    String ATTR_EXECUTABLE_PATH = ATTR_PREFIX + "executablePath";

    String getExecutablePath();

    void inject(ExternalEditorOptions options);

    boolean setOption(String name, Object value);
}
