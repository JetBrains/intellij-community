/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.internal.statistic.ideSettings;

import com.intellij.facet.frameworks.SettingsConnectionService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class IdeSettingsStatisticsService extends SettingsConnectionService {
  private static final Logger LOG = Logger.getInstance("#com.intellij.internal.statistic.ideSettings.IdeSettingsUsagesCollector");
  private static String FILE_NAME = "ide-settings-statistics.xml";

  private static final IdeSettingsStatisticsService myInstance = new IdeSettingsStatisticsService();
  private IdeSettingsDescriptor[] myDescriptors;

  public static IdeSettingsStatisticsService getInstance() {
    return myInstance;
  }

  private IdeSettingsStatisticsService() {
    super("http://jetbrains.com/idea/ide-settings-statistics.xml", "http://frameworks.jetbrains.com/statistics");
  }

  @NotNull
  public IdeSettingsDescriptor[] getSettingDescriptors() {
    if (myDescriptors == null) {
      final URL url = createVersionsUrl();
      if (url == null) return new IdeSettingsDescriptor[0];
      final IdeSettingsDescriptors descriptors = deserialize(url);
      myDescriptors = descriptors == null ? new IdeSettingsDescriptor[0] : descriptors.getDescriptors();
    }
    return myDescriptors;
  }

  @Nullable
  private static IdeSettingsDescriptors deserialize(@Nullable URL url) {
    if (url == null) return null;

    IdeSettingsDescriptors ideSettingsDescriptor = null;
    try {
      ideSettingsDescriptor = XmlSerializer.deserialize(url, IdeSettingsDescriptors.class);
    }
    catch (XmlSerializationException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        LOG.error(e);
      }
    }
    return ideSettingsDescriptor;
  }

  @Nullable
  private URL createVersionsUrl() {
    final String serviceUrl = getServiceUrl();
    if (StringUtil.isNotEmpty(serviceUrl)) {
      try {
        final String url = serviceUrl + "/" + FILE_NAME;
        HttpConfigurable.getInstance().prepareURL(url);

        return new URL(url);
      }
      catch (MalformedURLException e) {
        LOG.error(e);
      }
      catch (IOException e) {
        // no route to host, unknown host, etc.
      }
    }

    return null;
  }
}
