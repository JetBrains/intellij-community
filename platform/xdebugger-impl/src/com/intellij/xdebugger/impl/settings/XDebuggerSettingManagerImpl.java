// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.configurationStore.ComponentSerializationUtil;
import com.intellij.configurationStore.XmlSerializer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.SmartList;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

@State(name = "XDebuggerSettings", storages = @Storage("debugger.xml"))
public class XDebuggerSettingManagerImpl extends XDebuggerSettingsManager
  implements PersistentStateComponent<XDebuggerSettingManagerImpl.SettingsState>, Disposable {
  private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
  private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

  public static XDebuggerSettingManagerImpl getInstanceImpl() {
    return (XDebuggerSettingManagerImpl)XDebuggerSettingsManager.getInstance();
  }

  @Override
  public SettingsState getState() {
    SettingsState settingsState = new SettingsState();
    settingsState.setDataViewSettings(myDataViewSettings);
    settingsState.setGeneralSettings(myGeneralSettings);

    for (XDebuggerSettings settings : XDebuggerSettings.EXTENSION_POINT.getExtensionList()) {
      Object subState = settings.getState();
      if (subState != null) {
        Element serializedState = XmlSerializer.serialize(subState);
        if (serializedState != null) {
          SpecificSettingsState state = new SpecificSettingsState();
          state.id = settings.getId();
          state.configuration = serializedState;
          settingsState.specificStates.add(state);
        }
      }
    }
    return settingsState;
  }

  public Collection<XDebuggerSettings> getSettingsList() {
    return XDebuggerSettings.EXTENSION_POINT.getExtensionList();
  }

  @Override
  @NotNull
  public XDebuggerDataViewSettings getDataViewSettings() {
    return myDataViewSettings;
  }

  public XDebuggerGeneralSettings getGeneralSettings() {
    return myGeneralSettings;
  }

  @Override
  public void loadState(@NotNull final SettingsState state) {
    myDataViewSettings = state.getDataViewSettings();
    myGeneralSettings = state.getGeneralSettings();
    for (SpecificSettingsState settingsState : state.specificStates) {
      XDebuggerSettings<?> settings = findSettings(settingsState.id);
      if (settings != null) {
        ComponentSerializationUtil.loadComponentState(settings, settingsState.configuration);
      }
    }
  }

  @Override
  public void dispose() {
  }

  private static XDebuggerSettings findSettings(String id) {
    return XDebuggerSettings.EXTENSION_POINT.extensions().filter(e -> id.equals(e.getId())).findFirst().orElse(null);
  }

  public static class SettingsState {
    @XCollection(propertyElementName = "debuggers")
    public List<SpecificSettingsState> specificStates = new SmartList<>();
    private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
    private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

    @Property(surroundWithTag = false)
    public XDebuggerDataViewSettings getDataViewSettings() {
      return myDataViewSettings;
    }

    public void setDataViewSettings(XDebuggerDataViewSettings dataViewSettings) {
      myDataViewSettings = dataViewSettings;
    }

    @Property(surroundWithTag = false)
    public XDebuggerGeneralSettings getGeneralSettings() {
      return myGeneralSettings;
    }

    public void setGeneralSettings(XDebuggerGeneralSettings generalSettings) {
      myGeneralSettings = generalSettings;
    }
  }

  @Tag("debugger")
  static class SpecificSettingsState {
    @Attribute
    public String id;
    @Tag
    public Element configuration;
  }
}
