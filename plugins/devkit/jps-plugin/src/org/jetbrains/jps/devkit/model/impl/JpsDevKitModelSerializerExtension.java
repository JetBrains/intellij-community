// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModulePropertiesSerializer;

import java.util.Collections;
import java.util.List;

public class JpsDevKitModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsModulePropertiesSerializer<?>> getModulePropertiesSerializers() {
    return Collections.singletonList(new JpsPluginModulePropertiesSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Collections.singletonList(new JpsIdeaSdkPropertiesSerializer());
  }

  private static class JpsIdeaSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsSimpleElement<JpsIdeaSdkProperties>> {
    private static final String SANDBOX_HOME_FIELD = "mySandboxHome";
    private static final String JDK_NAME_ATTRIBUTE = "sdk";

    JpsIdeaSdkPropertiesSerializer() {
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

  private static final class JpsPluginModulePropertiesSerializer extends JpsModulePropertiesSerializer<JpsSimpleElement<JpsPluginModuleProperties>> {
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

