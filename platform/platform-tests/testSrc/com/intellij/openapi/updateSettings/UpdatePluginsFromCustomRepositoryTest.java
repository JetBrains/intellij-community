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
package com.intellij.openapi.updateSettings;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UpdatePluginsFromCustomRepositoryTest extends BareTestFixtureTestCase {
  @Test
  public void testOnlyCompatiblePluginsAreChecked() throws Exception {
    Map<PluginId, PluginDownloader> toUpdate = new LinkedHashMap<>();
    IdeaPluginDescriptor[] descriptors = new IdeaPluginDescriptor[]{loadDescriptor("plugin1.xml"), loadDescriptor("plugin2.xml")};

    BuildNumber currentBuildNumber = BuildNumber.fromString("IU-142.100");
    for (IdeaPluginDescriptor descriptor : descriptors) {
      PluginDownloader downloader = PluginDownloader.createDownloader(descriptor, null, currentBuildNumber);
      UpdateChecker.checkAndPrepareToInstall(downloader, new InstalledPluginsState(), toUpdate, new ArrayList<>(), null);
    }
    assertEquals("Found: " + toUpdate.size(), 1, toUpdate.size());

    PluginDownloader downloader = toUpdate.values().iterator().next();
    assertNotNull(downloader);
    assertEquals("0.1", downloader.getPluginVersion());
  }

  private IdeaPluginDescriptor loadDescriptor(String filePath) throws InvalidDataException, FileNotFoundException, MalformedURLException {
    String path = PlatformTestUtil.getCommunityPath() + "/platform/platform-tests/testData/updates/customRepositories/" + getTestName(true);
    File descriptorFile = new File(path, filePath);
    IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(descriptorFile.getParentFile());
    descriptor.readExternal(descriptorFile.toURI().toURL());
    return descriptor;
  }
}
