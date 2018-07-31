package com.intellij.openapi.updateSettings;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PluginDownloaderTest {

  @Test
  public void testSamePluginVersionAndDiffPlatformVersion() {
    assertEquals(1, PluginDownloader.compareVersionsSkipBrokenAndIncompatible(
      pluginDescriptor("135.0"),
      pluginDescriptor("139.3")));

    assertEquals(1, PluginDownloader.compareVersionsSkipBrokenAndIncompatible(
      pluginDescriptor(null),
      pluginDescriptor("139.0")));

    // don't care the case
    assertEquals(0, PluginDownloader.compareVersionsSkipBrokenAndIncompatible(
      pluginDescriptor("135.0"),
      pluginDescriptor(null)));

    assertEquals(0, PluginDownloader.compareVersionsSkipBrokenAndIncompatible(
      pluginDescriptor("135.0"),
      pluginDescriptor("133.2")));
  }

  static IdeaPluginDescriptor pluginDescriptor(String sinceBuild) {
    PluginNode node = new PluginNode(PluginId.getId("plugin-simple"));
    node.setVersion("0.4.2");
    node.setSinceBuild(sinceBuild);
    return node;
  }
}
