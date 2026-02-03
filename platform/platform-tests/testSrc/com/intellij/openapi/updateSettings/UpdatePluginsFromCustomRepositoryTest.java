// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings;

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginDescriptorLoadUtilsKt;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.UpdateChecker;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class UpdatePluginsFromCustomRepositoryTest extends BareTestFixtureTestCase {
  @Test
  public void testOnlyCompatiblePluginsAreChecked() throws Exception {
    Map<PluginId, PluginDownloader> toUpdate = new LinkedHashMap<>();
    Path base = Path.of(PlatformTestUtil.getPlatformTestDataPath(), "updates/customRepositories", getTestName(true));
    BuildNumber buildNumber = BuildNumber.fromString("IU-142.100");
    for (String name : new String[]{"plugin1.xml", "plugin2.xml"}) {
      IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoadUtilsKt
        .readDescriptorFromBytesForTest(base.resolve(name), false, Files.readAllBytes(base.resolve(name)),
                                        PluginId.getId("UpdatePluginsFromCustomRepositoryTest"));
      PluginDownloader downloader = PluginDownloader.createDownloader(descriptor, null, buildNumber);
      UpdateChecker.checkAndPrepareToInstall(downloader, new InstalledPluginsState(), toUpdate);
    }
    assertEquals("Found: " + toUpdate.size(), 1, toUpdate.size());

    PluginDownloader downloader = toUpdate.values().iterator().next();
    assertNotNull(downloader);
    assertEquals("0.1", downloader.getPluginVersion());
  }
}
