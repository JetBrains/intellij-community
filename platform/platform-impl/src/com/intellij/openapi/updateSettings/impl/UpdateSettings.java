/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(
  name = "UpdatesConfigurable",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/updates.xml", roamingType = RoamingType.DISABLED),
    @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)
  }
)
public class UpdateSettings implements PersistentStateComponent<Element>, UserUpdateSettings {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateSettings");

  public JDOMExternalizableStringList myPluginHosts = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList myKnownUpdateChannels = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList myIgnoredBuildNumbers = new JDOMExternalizableStringList();
  public JDOMExternalizableStringList myOutdatedPlugins = new JDOMExternalizableStringList();

  public boolean CHECK_NEEDED = true;
  public long LAST_TIME_CHECKED = 0;
  public String LAST_BUILD_CHECKED = "";
  public String UPDATE_CHANNEL_TYPE = ChannelStatus.RELEASE_CODE;
  public boolean SECURE_CONNECTION = false;

  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  public UpdateSettings() {
    updateDefaultChannel();
  }

  private void updateDefaultChannel() {
    if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
      UPDATE_CHANNEL_TYPE = ChannelStatus.EAP_CODE;
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public Element getState() {
    Element element = new Element("state");
    try {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    return element;
  }

  @SuppressWarnings("deprecation")
  @Override
  public void loadState(Element state) {
    try {
      DefaultJDOMExternalizer.readExternal(this, state);
    }
    catch (InvalidDataException e) {
      LOG.info(e);
    }
    updateDefaultChannel();
  }

  @NotNull
  @Override
  public List<String> getKnownChannelsIds() {
    List<String> ids = new ArrayList<String>();
    for (String channel : myKnownUpdateChannels) {
      ids.add(channel);
    }
    return ids;
  }

  @Override
  public void setKnownChannelIds(@NotNull List<String> ids) {
    myKnownUpdateChannels.clear();
    for (String id : ids) {
      myKnownUpdateChannels.add(id);
    }
  }

  public void forgetChannelId(String id) {
    myKnownUpdateChannels.remove(id);
  }

  @Override
  public List<String> getIgnoredBuildNumbers() {
    return myIgnoredBuildNumbers;
  }

  @NotNull
  @Override
  public ChannelStatus getSelectedChannelStatus() {
    return ChannelStatus.fromCode(UPDATE_CHANNEL_TYPE);
  }

  public List<String> getPluginHosts() {
    List<String> hosts = new ArrayList<String>(myPluginHosts);
    String pluginHosts = System.getProperty("idea.plugin.hosts");
    if (pluginHosts != null) {
      ContainerUtil.addAll(hosts, pluginHosts.split(";"));
    }
    return hosts;
  }

  public void forceCheckForUpdateAfterRestart() {
    LAST_TIME_CHECKED = 0;
  }

  public void saveLastCheckedInfo() {
    LAST_TIME_CHECKED = System.currentTimeMillis();
    LAST_BUILD_CHECKED = ApplicationInfo.getInstance().getBuild().asString();
  }
}
