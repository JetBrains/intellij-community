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
                                                        String baseModulePath,
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
