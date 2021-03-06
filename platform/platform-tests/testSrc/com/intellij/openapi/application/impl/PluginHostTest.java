// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.junit.Assert;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.core.IsNot.not;

public class PluginHostTest extends LightPlatformTestCase {
  public void testPluginsHostProperty() {
    String host = "IntellijIdeaRulezzz";

    String oldHost = System.setProperty(ApplicationInfoImpl.IDEA_PLUGINS_HOST_PROPERTY, host);

    try {
      ApplicationInfoImpl applicationInfo = new ApplicationInfoImpl(new Element("state"));
      Assert.assertThat(applicationInfo.getPluginManagerUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getPluginsListUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getPluginsDownloadUrl(), containsString(host));
      Assert.assertThat(applicationInfo.getChannelsListUrl(), containsString(host));

      Assert.assertThat(applicationInfo.getPluginManagerUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getPluginsListUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getPluginsDownloadUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
      Assert.assertThat(applicationInfo.getChannelsListUrl(), not(containsString(ApplicationInfoImpl.DEFAULT_PLUGINS_HOST)));
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
