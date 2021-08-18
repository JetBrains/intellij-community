// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.XmlElement;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginHostTest {
  @Test
  public void testPluginsHostProperty() {
    @SuppressWarnings("SpellCheckingInspection") String host = "IntellijIdeaRulezzz";
    PlatformTestUtil.withSystemProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host, () -> {
      ApplicationInfoImpl info = new ApplicationInfoImpl(
        new XmlElement("state", Map.of(), List.of(new XmlElement("plugins", Map.of(), List.of(), null)), null));
      assertThat(info.getPluginManagerUrl()).contains(host);
      assertThat(info.getPluginsListUrl()).contains(host);
      assertThat(info.getPluginsDownloadUrl()).contains(host);
      assertThat(info.getChannelsListUrl()).contains(host);

      assertThat(info.getPluginManagerUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsListUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsDownloadUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getChannelsListUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
    });
  }
}
