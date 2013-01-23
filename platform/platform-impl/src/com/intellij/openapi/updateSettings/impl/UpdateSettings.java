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
package com.intellij.openapi.updateSettings.impl;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
@State(
  name = "UpdatesConfigurable",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
public class UpdateSettings implements PersistentStateComponent<Element>, UserUpdateSettings {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateSettings"); 

  @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
  public JDOMExternalizableStringList myPluginHosts = new JDOMExternalizableStringList();

  @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
  public JDOMExternalizableStringList myKnownUpdateChannels = new JDOMExternalizableStringList();

  @SuppressWarnings({"WeakerAccess", "CanBeFinal"})
  public JDOMExternalizableStringList myIgnoredBuildNumbers = new JDOMExternalizableStringList();

  public boolean CHECK_NEEDED = true;
  public long LAST_TIME_CHECKED = 0;
  public String LAST_BUILD_CHECKED = "";
  public String UPDATE_CHANNEL_TYPE = ChannelStatus.RELEASE_CODE;

  public static UpdateSettings getInstance() {
    return ServiceManager.getService(UpdateSettings.class);
  }

  public UpdateSettings() {
    updateDefaultChannel();
  }

  public void saveLastCheckedInfo() {
    LAST_TIME_CHECKED = System.currentTimeMillis();
    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    LAST_BUILD_CHECKED = appInfo.getBuild().asString();
  }

  private void updateDefaultChannel() {
    if (ApplicationInfoImpl.getShadowInstance().isEAP()) {
      UPDATE_CHANNEL_TYPE = ChannelStatus.EAP_CODE;
    }
  }

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

  public void loadState(final Element state) {
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

  public void forceCheckForUpdateAfterRestart() {
    LAST_TIME_CHECKED = 0;
  }
}
