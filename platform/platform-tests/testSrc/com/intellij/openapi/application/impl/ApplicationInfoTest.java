// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.xml.dom.XmlElement;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationInfoTest {
  @Test
  public void shortenCompanyName() throws Exception {
    assertThat(createAppInfo(new XmlElement("company", Map.of("name", "Google Inc."), List.of(), null)).getShortCompanyName()).isEqualTo("Google");
    assertThat(createAppInfo(new XmlElement("company", Map.of("name", "JetBrains s.r.o."), List.of(), null)).getShortCompanyName()).isEqualTo("JetBrains");
    assertThat(createAppInfo(new XmlElement("company", Map.of("shortName", "Acme Inc."), List.of(), null)).getShortCompanyName()).isEqualTo("Acme Inc.");
  }

  @Test
  public void pluginsHostProperty() {
    @SuppressWarnings("SpellCheckingInspection") String host = "IntellijIdeaRulezzz";
    PlatformTestUtil.withSystemProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host, () -> {
      ApplicationInfoImpl info = createAppInfo();
      assertThat(info.getPluginManagerUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsListUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getPluginsDownloadUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
      assertThat(info.getChannelsListUrl()).contains(host).doesNotContain(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST);
    });
  }

  public static @NotNull ApplicationInfoImpl createAppInfo(@NotNull XmlElement @NotNull ... content) {
    List<XmlElement> children = new ArrayList<>(List.of(content));
    children.add(new XmlElement("plugins", Map.of(), List.of(), null));
    return new ApplicationInfoImpl(new XmlElement("state", Map.of(), children, null));
  }
}
