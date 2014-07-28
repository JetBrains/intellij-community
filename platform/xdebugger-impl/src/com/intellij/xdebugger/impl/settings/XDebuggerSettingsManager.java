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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 * todo rename to XDebuggerSettingsManagerImpl
 */
@SuppressWarnings("ClassNameSameAsAncestorName")
@State(
    name = XDebuggerSettingsManager.COMPONENT_NAME,
    storages = {
      @Storage(
          file = StoragePathMacros.APP_CONFIG + "/other.xml"
      )
    }
)
public class XDebuggerSettingsManager extends com.intellij.xdebugger.settings.XDebuggerSettingsManager implements PersistentStateComponent<XDebuggerSettingsManager.SettingsState> {
  @NonNls public static final String COMPONENT_NAME = "XDebuggerSettings";
  private Map<String, XDebuggerSettings<?>> mySettingsById;
  private Map<Class<? extends XDebuggerSettings>, XDebuggerSettings<?>> mySettingsByClass;
  private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
  private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

  public static XDebuggerSettingsManager getInstanceImpl() {
    return (XDebuggerSettingsManager)com.intellij.xdebugger.settings.XDebuggerSettingsManager.getInstance();
  }

  @Override
  public SettingsState getState() {
    SettingsState settingsState = new SettingsState();
    settingsState.setDataViewSettings(myDataViewSettings);
    settingsState.setGeneralSettings(myGeneralSettings);
    for (XDebuggerSettings<?> settings : getSettingsList()) {
      Object subState = settings.getState();
      if (subState != null) {
        SpecificSettingsState state = new SpecificSettingsState();
        state.setId(settings.getId());
        state.setSettingsElement(XmlSerializer.serialize(subState, new SkipDefaultValuesSerializationFilters()));
        settingsState.getSpecificStates().add(state);
      }
    }
    return settingsState;
  }

  public Collection<XDebuggerSettings<?>> getSettingsList() {
    initSettings();
    return Collections.unmodifiableCollection(mySettingsById.values());
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
  public void loadState(final SettingsState state) {
    myDataViewSettings = state.getDataViewSettings();
    myGeneralSettings = state.getGeneralSettings();
    for (SpecificSettingsState settingsState : state.getSpecificStates()) {
      XDebuggerSettings<?> settings = findSettings(settingsState.getId());
      if (settings != null) {
        ComponentSerializationUtil.loadComponentState(settings, settingsState.getSettingsElement());
      }
    }
  }

  private XDebuggerSettings findSettings(String id) {
    initSettings();
    return mySettingsById.get(id);
  }

  private void initSettings() {
    if (mySettingsById == null) {
      XDebuggerSettings[] extensions = XDebuggerSettings.EXTENSION_POINT.getExtensions();
      mySettingsById = new LinkedHashMap<String, XDebuggerSettings<?>>(extensions.length);
      mySettingsByClass = new LinkedHashMap<Class<? extends XDebuggerSettings>, XDebuggerSettings<?>>(extensions.length);
      for (XDebuggerSettings settings : extensions) {
        mySettingsById.put(settings.getId(), settings);
        mySettingsByClass.put(settings.getClass(), settings);
      }
    }
  }

  public <T extends XDebuggerSettings<?>> T getSettings(final Class<T> aClass) {
    initSettings();
    //noinspection unchecked
    return (T)mySettingsByClass.get(aClass);
  }

  public static class SettingsState {
    private List<SpecificSettingsState> mySpecificStates = new ArrayList<SpecificSettingsState>();
    private XDebuggerDataViewSettings myDataViewSettings = new XDebuggerDataViewSettings();
    private XDebuggerGeneralSettings myGeneralSettings = new XDebuggerGeneralSettings();

    @Tag("debuggers")
    @AbstractCollection(surroundWithTag = false)
    public List<SpecificSettingsState> getSpecificStates() {
      return mySpecificStates;
    }

    public void setSpecificStates(final List<SpecificSettingsState> specificStates) {
      mySpecificStates = specificStates;
    }

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
  public static class SpecificSettingsState {
    private String myId;
    private Element mySettingsElement;


    @Attribute("id")
    public String getId() {
      return myId;
    }

    @Tag("configuration")
    public Element getSettingsElement() {
      return mySettingsElement;
    }

    public void setSettingsElement(final Element settingsElement) {
      mySettingsElement = settingsElement;
    }

    public void setId(final String id) {
      myId = id;
    }


  }
}
