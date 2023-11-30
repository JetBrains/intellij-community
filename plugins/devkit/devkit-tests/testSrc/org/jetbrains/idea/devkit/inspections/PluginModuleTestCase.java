/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.module.PluginModuleType;

public abstract class PluginModuleTestCase extends LightJavaCodeInsightFixtureTestCase {

  private static final DefaultLightProjectDescriptor ourProjectDescriptor = new DefaultLightProjectDescriptor() {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      addJetBrainsAnnotations(model);
    }

    @NotNull
    @Override
    public String getModuleTypeId() {
      return PluginModuleType.ID;
    }
  };

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourProjectDescriptor;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getPluginBuildConfiguration().cleanupForNextTest();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void setPluginXml(@TestDataFile String pluginXml) {
    final VirtualFile file = myFixture.copyFileToProject(pluginXml, "META-INF/plugin.xml");
    final PluginBuildConfiguration pluginBuildConfiguration = getPluginBuildConfiguration();
    pluginBuildConfiguration.setPluginXmlFromVirtualFile(file);
  }

  @NotNull
  private PluginBuildConfiguration getPluginBuildConfiguration() {
    final PluginBuildConfiguration pluginBuildConfiguration = PluginBuildConfiguration.getInstance(getModule());
    assertNotNull(pluginBuildConfiguration);
    return pluginBuildConfiguration;
  }
}
