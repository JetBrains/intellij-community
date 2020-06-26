// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.gradle.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.gradle.model.artifacts.JpsGradleArtifactExtension;
import org.jetbrains.jps.gradle.model.impl.artifacts.GradleArtifactExtensionProperties;
import org.jetbrains.jps.gradle.model.impl.artifacts.JpsGradleArtifactExtensionImpl;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JDomSerializationUtil;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactExtensionSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class JpsGradleModelSerializationExtension extends JpsModelSerializerExtension {
  private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";
  private static final String GRADLE_SYSTEM_ID = "GRADLE";

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    Element externalSystemComponent = JDomSerializationUtil.findComponent(rootElement, "ExternalSystem");
    if (GRADLE_SYSTEM_ID.equals(rootElement.getAttributeValue("external.system.id"))) {
      JpsGradleExtensionService.getInstance().getOrCreateExtension(module, rootElement.getAttributeValue("external.system.module.type"));
    }
    else if (externalSystemComponent != null && GRADLE_SYSTEM_ID.equals(externalSystemComponent.getAttributeValue("externalSystem"))) {
      JpsGradleExtensionService.getInstance().getOrCreateExtension(module, externalSystemComponent.getAttributeValue("externalSystemModuleType"));
    }
  }

  @Override
  public void saveModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    if (JpsGradleExtensionService.getInstance().getExtension(module) != null) {
      rootElement.setAttribute("external.system.id", "GRADLE");
    }
  }

  @Override
  public void loadModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    if (orderEntry.getAttributeValue(PRODUCTION_ON_TEST_ATTRIBUTE) != null) {
      JpsGradleExtensionService.getInstance().setProductionOnTestDependency(dependency, true);
    }
  }

  @Override
  public void saveModuleDependencyProperties(JpsDependencyElement dependency, Element orderEntry) {
    if (JpsGradleExtensionService.getInstance().isProductionOnTestDependency(dependency)) {
      orderEntry.setAttribute(PRODUCTION_ON_TEST_ATTRIBUTE, "");
    }
  }

  @NotNull
  @Override
  public List<? extends JpsArtifactExtensionSerializer<?>> getArtifactExtensionSerializers() {
    return Collections.singletonList(
      new JpsGradleArtifactExtensionSerializer("gradle-properties", JpsGradleArtifactExtensionImpl.ROLE));
  }

  private static final class JpsGradleArtifactExtensionSerializer extends JpsArtifactExtensionSerializer<JpsGradleArtifactExtension> {
    private JpsGradleArtifactExtensionSerializer(final String id, final JpsElementChildRole<JpsGradleArtifactExtension> role) {
      super(id, role);
    }

    @Override
    public JpsGradleArtifactExtension loadExtension(@Nullable Element optionsTag) {
      return new JpsGradleArtifactExtensionImpl(
        optionsTag != null ? XmlSerializer.deserialize(optionsTag, GradleArtifactExtensionProperties.class) : null);
    }

    @Override
    public void saveExtension(@NotNull JpsGradleArtifactExtension extension, @NotNull Element optionsTag) {
      GradleArtifactExtensionProperties properties = extension.getProperties();
      XmlSerializer.serializeInto(properties, optionsTag, new SkipDefaultValuesSerializationFilters());
    }
  }
}
