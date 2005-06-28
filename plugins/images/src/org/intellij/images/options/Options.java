package org.intellij.images.options;

import java.beans.PropertyChangeListener;

/**
 * Options for plugin.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public interface Options extends Cloneable {
    EditorOptions getEditorOptions();

    ExternalEditorOptions getExternalEditorOptions();

    /**
     * Option injection from other options.
     *
     * @param options Other options
     */
    void inject(Options options);

    void addPropertyChangeListener(PropertyChangeListener listener);

    void addPropertyChangeListener(String propertyName, PropertyChangeListener listener);

    void removePropertyChangeListener(PropertyChangeListener listener);

    void removePropertyChangeListener(String propertyName, PropertyChangeListener listener);

    /**
     * Set option by string representation.
     *
     * @param name  Name of option
     * @param value Value
     * @return <code>true</code> if option is matched and setted.
     */
    boolean setOption(String name, Object value);
}
