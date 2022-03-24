// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.emptyList;
import static com.intellij.util.containers.ContainerUtil.map2List;

@State(name = "Vcs.Log.Tabs.Properties", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@Service(Service.Level.PROJECT)
public final class VcsLogProjectTabsProperties implements PersistentStateComponent<VcsLogProjectTabsProperties.State>,
                                                          VcsLogTabsProperties {
  private static final int RECENTLY_FILTERED_VALUES_LIMIT = 10;
  @NotNull private final VcsLogApplicationSettings myAppSettings;
  @NotNull private State myState = new State();

  public VcsLogProjectTabsProperties() {
    myAppSettings = ApplicationManager.getApplication().getService(VcsLogApplicationSettings.class);
  }

  @NotNull
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @Override
  @NotNull
  public MainVcsLogUiProperties createProperties(@NotNull final String id) {
    myState.TAB_STATES.putIfAbsent(id, new MyState());
    return new MyVcsLogUiPropertiesImpl(id);
  }

  public void addTab(@NotNull String tabId, @NotNull VcsLogTabLocation location) {
    myState.OPEN_GENERIC_TABS.put(tabId, location);
  }

  public void removeTab(@NotNull String tabId) {
    myState.OPEN_GENERIC_TABS.remove(tabId);
  }

  public void resetState(@NotNull String tabId) {
    myState.TAB_STATES.put(tabId, null);
  }

  @NotNull
  public Map<String, VcsLogTabLocation> getTabs() {
    return myState.OPEN_GENERIC_TABS;
  }

  public static void addRecentGroup(@NotNull Map<String, List<RecentGroup>> stateField,
                                    @NotNull String filterName,
                                    @NotNull Collection<String> values) {
    List<RecentGroup> recentGroups = stateField.get(filterName);
    if (recentGroups == null) {
      recentGroups = new ArrayList<>();
      stateField.put(filterName, recentGroups);
    }
    RecentGroup group = new RecentGroup(values);
    recentGroups.remove(group);
    recentGroups.add(0, group);
    while (recentGroups.size() > RECENTLY_FILTERED_VALUES_LIMIT) {
      recentGroups.remove(recentGroups.size() - 1);
    }
  }

  @NotNull
  public static List<List<String>> getRecentGroup(@NotNull Map<String, List<RecentGroup>> stateField, @NotNull String filterName) {
    List<RecentGroup> values = stateField.get(filterName);
    if (values == null) {
      return emptyList();
    }
    return map2List(values, group -> group.FILTER_VALUES);
  }

  public static class State {
    public Map<String, MyState> TAB_STATES = new TreeMap<>();
    public LinkedHashMap<String, VcsLogTabLocation> OPEN_GENERIC_TABS = new LinkedHashMap<>();
    public Map<String, List<RecentGroup>> RECENT_FILTERS = new HashMap<>();
  }

  public static class RecentGroup {
    @XCollection
    public List<String> FILTER_VALUES = new ArrayList<>();

    public RecentGroup() {
    }

    public RecentGroup(@NotNull Collection<String> values) {
      FILTER_VALUES.addAll(values);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      RecentGroup group = (RecentGroup)o;
      return Comparing.haveEqualElements(FILTER_VALUES, group.FILTER_VALUES);
    }

    @Override
    public int hashCode() {
      return Comparing.unorderedHashcode(FILTER_VALUES);
    }
  }

  private class MyVcsLogUiPropertiesImpl extends VcsLogUiPropertiesImpl<MyState> {
    private final String myId;

    MyVcsLogUiPropertiesImpl(String id) {
      super(myAppSettings);
      myId = id;
    }

    @NotNull
    @Override
    public MyState getState() {
      MyState state = myState.TAB_STATES.get(myId);
      if (state == null) {
        state = new MyState();
        myState.TAB_STATES.put(myId, state);
      }
      return state;
    }

    @Override
    public void loadState(@NotNull MyState state) {
      myState.TAB_STATES.put(myId, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @NotNull T get(@NotNull VcsLogUiProperty<T> property) {
      if (property instanceof CustomBooleanTabProperty) {
        Boolean value = getState().CUSTOM_BOOLEAN_PROPERTIES.get(property.getName());
        if (value == null) {
          value = ((CustomBooleanTabProperty)property).defaultValue(myId);
        }
        return (T)value;
      }
      return super.get(property);
    }

    @Override
    public <T> void set(@NotNull VcsLogUiProperty<T> property, @NotNull T value) {
      if (property instanceof CustomBooleanTabProperty) {
        getState().CUSTOM_BOOLEAN_PROPERTIES.put(property.getName(), (Boolean)value);
        onPropertyChanged(property);
        return;
      }
      super.set(property, value);
    }

    @Override
    public <T> boolean exists(@NotNull VcsLogUiProperty<T> property) {
      return super.exists(property) || property instanceof CustomBooleanTabProperty;
    }

    @Override
    public void addRecentlyFilteredGroup(@NotNull String filterName, @NotNull Collection<String> values) {
      addRecentGroup(myState.RECENT_FILTERS, filterName, values);
    }

    @NotNull
    @Override
    public List<List<String>> getRecentlyFilteredGroups(@NotNull String filterName) {
      return getRecentGroup(myState.RECENT_FILTERS, filterName);
    }
  }

  @Tag("State")
  private static class MyState extends VcsLogUiPropertiesImpl.State {
    public Map<String, Boolean> CUSTOM_BOOLEAN_PROPERTIES = new HashMap<>();
  }

  public static class CustomBooleanTabProperty extends VcsLogUiProperties.VcsLogUiProperty<Boolean> {
    public CustomBooleanTabProperty(@NotNull @NonNls String name) {
      super(name);
    }

    @NotNull
    public Boolean defaultValue(@NotNull String logId) {
      return Boolean.FALSE;
    }
  }
}