// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class JpsAppEngineModelSerializerExtension extends JpsModelSerializerExtension {
  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return Collections.singletonList(new JpsAppEngineModuleExtensionSerializer());
  }

  private static final class JpsAppEngineModuleExtensionSerializer extends JpsFacetConfigurationSerializer<JpsAppEngineModuleExtension> {
    JpsAppEngineModuleExtensionSerializer() {
      super(JpsAppEngineModuleExtensionImpl.ROLE, "google-app-engine", "Google App Engine");
    }

    @Override
    protected JpsAppEngineModuleExtension loadExtension(@NotNull Element facetConfigurationElement,
                                                        String name,
                                                        JpsElement parent,
                                                        JpsModule module) {
      return new JpsAppEngineModuleExtensionImpl(
        XmlSerializer.deserialize(facetConfigurationElement, AppEngineModuleExtensionProperties.class));
    }
  }
}
