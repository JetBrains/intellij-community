/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.gradle.model.impl;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.gradle.model.JpsGradleExtensionService;
import org.jetbrains.jps.model.module.JpsDependencyElement;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

/**
 * @author Vladislav.Soroka
 * @since 7/10/2014
 */
public class JpsGradleModelSerializationExtension extends JpsModelSerializerExtension {
  private static final String PRODUCTION_ON_TEST_ATTRIBUTE = "production-on-test";

  @Override
  public void loadModuleOptions(@NotNull JpsModule module, @NotNull Element rootElement) {
    if ("GRADLE".equals(rootElement.getAttributeValue("external.system.id"))) {
      JpsGradleExtensionService.getInstance().getOrCreateExtension(module, rootElement);
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
}
