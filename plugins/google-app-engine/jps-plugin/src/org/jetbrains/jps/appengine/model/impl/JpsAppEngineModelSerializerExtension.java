/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.jps.appengine.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsAppEngineModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(new JpsAppEngineModuleExtensionSerializer());
  }

  private static class JpsAppEngineModuleExtensionSerializer extends JpsFacetConfigurationSerializer<JpsAppEngineModuleExtension> {
    public JpsAppEngineModuleExtensionSerializer() {
      super(JpsAppEngineModuleExtensionImpl.ROLE, "google-app-engine", "Google App Engine");
    }

    @Override
    protected JpsAppEngineModuleExtension loadExtension(@NotNull Element facetConfigurationElement,
                                                        String name,
                                                        JpsElement parent,
                                                        JpsModule module) {
      AppEngineModuleExtensionProperties properties = XmlSerializer.deserialize(facetConfigurationElement, AppEngineModuleExtensionProperties.class);
      return new JpsAppEngineModuleExtensionImpl(properties != null ? properties : new AppEngineModuleExtensionProperties());
    }

    @Override
    protected void saveExtension(JpsAppEngineModuleExtension extension, Element facetConfigurationTag, JpsModule module) {
      XmlSerializer.serializeInto(((JpsAppEngineModuleExtensionImpl)extension).getProperties(), facetConfigurationTag);
    }
  }
}
