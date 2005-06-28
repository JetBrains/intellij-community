package org.intellij.images.options.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.jdom.Element;

/**
 * Options configurable manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class OptionsManagerImpl extends OptionsManager implements NamedJDOMExternalizable, ApplicationComponent {
    private static final String CONFIGURATION_NAME = "images.support";
    private static final String NAME = "Images.OptionsManager";
    private Options options = new OptionsImpl();

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public void readExternal(Element element) throws InvalidDataException {
        ((JDOMExternalizable)options).readExternal(element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
        ((JDOMExternalizable)options).writeExternal(element);
    }

    public Options getOptions() {
        return options;
    }

    public String getExternalFileName() {
        return CONFIGURATION_NAME;
    }
}
