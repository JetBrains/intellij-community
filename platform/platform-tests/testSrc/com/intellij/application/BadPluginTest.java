/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.application;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 * @since 9.01.2013
 */
public class BadPluginTest extends PlatformTestCase {
  private static final String COM_YOUR_COMPANY_UNIQUE_PLUGIN_ID = "com.your.company.unique.plugin.id";

  public void testBadPlugin() throws Exception {
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(PluginId.getId(COM_YOUR_COMPANY_UNIQUE_PLUGIN_ID));
    if (plugin == null) return;
    List<String> disabledPlugins = new ArrayList<>();
    PluginManagerCore.loadDisabledPlugins(PathManager.getConfigPath(), disabledPlugins);
    assertEquals(1, disabledPlugins.size());
    assertEquals(COM_YOUR_COMPANY_UNIQUE_PLUGIN_ID, disabledPlugins.get(0));
  }

  @Override
  protected void setUp() throws Exception {
    String path = PlatformTestUtil.getCommunityPath() + "/platform/platform-tests/testData/badPlugins";
    File directory = createTempDirectory(false);
    FileUtil.copyDir(new File(path), directory);

    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, directory.getPath());
    System.out.println("Old path: " + myOldConfigPath);
    System.out.println("New path: " + System.getProperty(PathManager.PROPERTY_CONFIG_PATH));
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    System.setProperty(PathManager.PROPERTY_CONFIG_PATH, myOldConfigPath);
    super.tearDown();
  }

  private String myOldConfigPath = System.getProperty(PathManager.PROPERTY_CONFIG_PATH);
}
