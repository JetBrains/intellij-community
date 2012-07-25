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
import org.jetbrains.jps.model.serialization.JpsLoaderBase;
import org.jetbrains.jps.model.serialization.JpsModelLoaderExtension;
import org.jetbrains.jps.model.serialization.JpsModulePropertiesLoader;
import org.jetbrains.jps.model.serialization.JpsSdkPropertiesLoader;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JpsDevKitModelLoaderExtension extends JpsModelLoaderExtension {
  @NotNull
  @Override
  public List<? extends JpsModulePropertiesLoader<?>> getModulePropertiesLoaders() {
    return Arrays.asList(new JpsPluginModulePropertiesLoader());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesLoader<?>> getSdkPropertiesLoaders() {
    return Arrays.asList(new JpsIdeaSdkPropertiesLoader());
  }

  private static class JpsIdeaSdkPropertiesLoader extends JpsSdkPropertiesLoader<JpsIdeaSdkProperties> {
    public JpsIdeaSdkPropertiesLoader() {
      super("IDEA JDK", JpsIdeaSdkType.INSTANCE);
    }

    @Override
    public JpsIdeaSdkProperties loadProperties(String homePath, String version, @Nullable Element propertiesElement) {
      String sandboxHome = null;
      String jdkName = null;
      if (propertiesElement != null) {
        sandboxHome = JDOMExternalizerUtil.readField(propertiesElement, "mySandboxHome");
        jdkName = propertiesElement.getAttributeValue("sdk");
      }
      return new JpsIdeaSdkProperties(homePath, version, sandboxHome, jdkName);
    }
  }

  private static class JpsPluginModulePropertiesLoader extends JpsModulePropertiesLoader<JpsPluginModuleProperties> {
    private JpsPluginModulePropertiesLoader() {
      super(JpsPluginModuleType.INSTANCE, "PLUGIN_MODULE");
    }

    @Override
    public JpsPluginModuleProperties loadProperties(@Nullable Element moduleRootElement) {
      Element component = JpsLoaderBase.findComponent(moduleRootElement, "DevKit.ModuleBuildProperties");
      return new JpsPluginModuleProperties(component != null ? component.getAttributeValue("url") : null);
    }
  }
}

