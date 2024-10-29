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
package org.jetbrains.jps.devkit.model;

import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModule;
import org.jetbrains.jps.model.serialization.JpsProjectData;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.UsefulTestCase.assertOneElement;
import static org.junit.jupiter.api.Assertions.*;

public class JpsPluginProjectSerializationTest {
  @Test
  public void testLoadProject() {
    JpsProjectData projectData =
      JpsProjectData.loadFromTestData("plugins/devkit/jps-plugin/testData/pluginProject/pluginProject.ipr", getClass());
    JpsProject project = projectData.getProject();
    JpsModule module = assertOneElement(project.getModules());
    assertEquals(JpsPluginModuleType.INSTANCE, module.getModuleType());
    JpsTypedModule<JpsSimpleElement<JpsPluginModuleProperties>> pluginModule = module.asTyped(JpsPluginModuleType.INSTANCE);
    assertNotNull(pluginModule);
    String url = pluginModule.getProperties().getData().getPluginXmlUrl();
    assertEquals(projectData.getUrl("META-INF/plugin.xml"), url);

    JpsGlobal global = project.getModel().getGlobal();
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> javaSdk = global.addSdk("1.6", null, null, JpsJavaSdkType.INSTANCE);
    JpsSimpleElement<JpsIdeaSdkProperties> properties =
      JpsElementFactory.getInstance().createSimpleElement(new JpsIdeaSdkProperties(null, "1.6"));
    JpsTypedLibrary<JpsSdk<JpsSimpleElement<JpsIdeaSdkProperties>>> pluginSdk = global
      .addSdk("IDEA plugin SDK", null, null, JpsIdeaSdkType.INSTANCE, properties);
    assertSame(pluginSdk.getProperties(), module.getSdk(JpsIdeaSdkType.INSTANCE));
    assertSame(javaSdk.getProperties(), module.getSdk(JpsJavaSdkType.INSTANCE));
  }
}
