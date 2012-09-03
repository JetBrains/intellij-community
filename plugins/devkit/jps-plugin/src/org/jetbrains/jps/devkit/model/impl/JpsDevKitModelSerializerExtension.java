/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.devkit.model.impl;

import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.devkit.model.JpsIdeaSdkProperties;
import org.jetbrains.jps.devkit.model.JpsIdeaSdkType;
import org.jetbrains.jps.devkit.model.JpsPluginModuleProperties;
import org.jetbrains.jps.devkit.model.JpsPluginModuleType;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.serialization.*;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JpsDevKitModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Arrays.asList(new JpsPluginModulePropertiesSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Arrays.asList(new JpsIdeaSdkPropertiesSerializer());
  }

  private static class JpsIdeaSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsSimpleElement<JpsIdeaSdkProperties>> {
    private static final String SANDBOX_HOME_FIELD = "mySandboxHome";
    private static final String JDK_NAME_ATTRIBUTE = "sdk";

    public JpsIdeaSdkPropertiesSerializer() {
      super("IDEA JDK", JpsIdeaSdkType.INSTANCE);
    }

    @NotNull
    @Override
    public JpsSimpleElement<JpsIdeaSdkProperties> loadProperties(@Nullable Element propertiesElement) {
      String sandboxHome = null;
      String jdkName = null;
      if (propertiesElement != null) {
        sandboxHome = JDOMExternalizerUtil.readField(propertiesElement, SANDBOX_HOME_FIELD);
        jdkName = propertiesElement.getAttributeValue(JDK_NAME_ATTRIBUTE);
      }
      return JpsElementFactory.getInstance().createSimpleElement(new JpsIdeaSdkProperties(sandboxHome, jdkName));
    }

    @Override
    public void saveProperties(@NotNull JpsSimpleElement<JpsIdeaSdkProperties> properties, @NotNull Element element) {
      JDOMExternalizerUtil.writeField(element, SANDBOX_HOME_FIELD, properties.getData().getSandboxHome());
      element.setAttribute(JDK_NAME_ATTRIBUTE, properties.getData().getJdkName());
    }
  }

  private static class JpsPluginModulePropertiesSerializer extends JpsModulePropertiesSerializer<JpsSimpleElement<JpsPluginModuleProperties>> {
    private static final String URL_ATTRIBUTE = "url";
    private static final String MANIFEST_ATTRIBUTE = "manifest";

    private JpsPluginModulePropertiesSerializer() {
      super(JpsPluginModuleType.INSTANCE, "PLUGIN_MODULE", "DevKit.ModuleBuildProperties");
    }

    @Override
    public JpsSimpleElement<JpsPluginModuleProperties> loadProperties(@Nullable Element componentElement) {
      String pluginXmlUrl = componentElement != null ? componentElement.getAttributeValue(URL_ATTRIBUTE) : null;
      String manifestFileUrl = componentElement != null ? componentElement.getAttributeValue(MANIFEST_ATTRIBUTE) : null;
      return JpsElementFactory.getInstance().createSimpleElement(new JpsPluginModuleProperties(pluginXmlUrl, manifestFileUrl));
    }

    @Override
    public void saveProperties(@NotNull JpsSimpleElement<JpsPluginModuleProperties> element, @NotNull Element componentElement) {
      String pluginXmlUrl = element.getData().getPluginXmlUrl();
      if (pluginXmlUrl != null) {
        componentElement.setAttribute(URL_ATTRIBUTE, pluginXmlUrl);
      }
      String manifestFileUrl = element.getData().getManifestFileUrl();
      if (manifestFileUrl != null) {
        componentElement.setAttribute(MANIFEST_ATTRIBUTE, manifestFileUrl);
      }
    }
  }
}

