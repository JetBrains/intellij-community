// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
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
      UpdateChecker.checkAndPrepareToInstall(downloader, new InstalledPluginsState(), toUpdate, null, null);
    }
    assertEquals("Found: " + toUpdate.size(), 1, toUpdate.size());

    PluginDownloader downloader = toUpdate.values().iterator().next();
    assertNotNull(downloader);
    assertEquals("0.1", downloader.getPluginVersion());
  }

  @NotNull
  private IdeaPluginDescriptor loadDescriptor(String filePath) throws IOException, JDOMException {
    String path = PlatformTestUtil.getPlatformTestDataPath() + "updates/customRepositories/" + getTestName(true);
    Path descriptorFile = Paths.get(path, filePath);
    IdeaPluginDescriptorImpl descriptor = new IdeaPluginDescriptorImpl(descriptorFile.getParent(), descriptorFile.getParent(), false);
    PluginManager.loadDescriptorFromFile(descriptor, descriptorFile, null, Collections.emptySet());
    return descriptor;
  }
}