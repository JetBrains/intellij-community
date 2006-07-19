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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Options configurable manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class OptionsManagerImpl extends OptionsManager implements NamedJDOMExternalizable, ApplicationComponent {
    @NonNls private static final String CONFIGURATION_NAME = "images.support";
    @NonNls private static final String NAME = "Images.OptionsManager";
    private Options options = new OptionsImpl();

    @NotNull
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
