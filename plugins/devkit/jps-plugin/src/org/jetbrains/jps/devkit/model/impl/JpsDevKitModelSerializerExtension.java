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
import org.jetbrains.jps.model.serialization.*;
import org.jetbrains.jps.model.serialization.JpsModulePropertiesSerializer;
import org.jetbrains.jps.model.serialization.JpsSdkPropertiesSerializer;

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
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesLoaders() {
    return Arrays.asList(new JpsIdeaSdkPropertiesSerializer());
  }

  private static class JpsIdeaSdkPropertiesSerializer extends JpsSdkPropertiesSerializer<JpsIdeaSdkProperties> {
    private static final String SANDBOX_HOME_FIELD = "mySandboxHome";
    private static final String JDK_NAME_ATTRIBUTE = "sdk";

    public JpsIdeaSdkPropertiesSerializer() {
      super("IDEA JDK", JpsIdeaSdkType.INSTANCE);
    }

    @NotNull
    @Override
    public JpsIdeaSdkProperties loadProperties(String homePath, String version, @Nullable Element propertiesElement) {
      String sandboxHome = null;
      String jdkName = null;
      if (propertiesElement != null) {
        sandboxHome = JDOMExternalizerUtil.readField(propertiesElement, SANDBOX_HOME_FIELD);
        jdkName = propertiesElement.getAttributeValue(JDK_NAME_ATTRIBUTE);
      }
      return new JpsIdeaSdkProperties(homePath, version, sandboxHome, jdkName);
    }

    @Override
    public void saveProperties(@NotNull JpsIdeaSdkProperties properties, @NotNull Element element) {
      JDOMExternalizerUtil.writeField(element, SANDBOX_HOME_FIELD, properties.getSandboxHome());
      element.setAttribute(JDK_NAME_ATTRIBUTE, properties.getJdkName());
    }
  }

  private static class JpsPluginModulePropertiesSerializer extends JpsModulePropertiesSerializer<JpsPluginModuleProperties> {
    private JpsPluginModulePropertiesSerializer() {
      super(JpsPluginModuleType.INSTANCE, "PLUGIN_MODULE");
    }

    @Override
    public JpsPluginModuleProperties loadProperties(@Nullable Element moduleRootElement) {
      Element component = JpsLoaderBase.findComponent(moduleRootElement, "DevKit.ModuleBuildProperties");
      return new JpsPluginModuleProperties(component != null ? component.getAttributeValue("url") : null);
    }
  }
}

