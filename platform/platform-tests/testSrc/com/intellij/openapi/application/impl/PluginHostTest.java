// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.util.XmlElement;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginHostTest {
  public void testPluginsHostProperty() {
    String host = "IntellijIdeaRulezzz";
    String oldHost = System.setProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host);
    try {
      ApplicationInfoImpl info = new ApplicationInfoImpl(new XmlElement("state", Collections.emptyMap(), Collections.emptyList(), null));
      assertThat(info.getPluginManagerUrl()).contains(host);
      assertThat(info.getPluginsListUrl()).contains(host);
      assertThat(info.getPluginsDownloadUrl()).contains(host);
      assertThat(info.getChannelsListUrl()).contains(host);

      assertThat(info.getPluginManagerUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsListUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsDownloadUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getChannelsListUrl()).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
    }
    finally {
      if (oldHost == null) {
        System.clearProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY);
      }
      else {
        System.setProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, oldHost);
      }
    }
  }
}
