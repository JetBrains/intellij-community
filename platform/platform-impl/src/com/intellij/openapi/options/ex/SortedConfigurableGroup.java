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
package com.intellij.openapi.options.ex;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author Sergey.Malenkov
 */
public final class SortedConfigurableGroup
  extends SearchableConfigurable.Parent.Abstract
  implements SearchableConfigurable, ConfigurableGroup, Configurable.NoScroll {

  private final ArrayList<WeightConfigurable> myList = new ArrayList<WeightConfigurable>();
  private final String myGroupId;
  private String myDisplayName;

  private SortedConfigurableGroup(String groupId) {
    myGroupId = groupId;
  }

  public SortedConfigurableGroup(Project project, Configurable... configurables) {
    myGroupId = "root";
    // create groups from configurations
    HashMap<String, SortedConfigurableGroup> map = new HashMap<String, SortedConfigurableGroup>();
    map.put(myGroupId, this);
    for (Configurable configurable : configurables) {
      int weight = 0;
      String groupId = null;
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        weight = wrapper.getExtensionPoint().groupWeight;
        groupId = wrapper.getExtensionPoint().groupId;
      }
      SortedConfigurableGroup composite = map.get(groupId);
      if (composite == null) {
        composite = new SortedConfigurableGroup(groupId);
        map.put(groupId, composite);
      }
      composite.add(weight, configurable);
    }
    // process supported groups
    add(70, map.remove("appearance"));
    add(60, map.remove("editor"));
    SortedConfigurableGroup projectGroup = map.remove("project");
    if (projectGroup != null && project != null && !project.isDefault()) {
      projectGroup.myDisplayName = StringUtil.first(
        OptionsBundle.message("configurable.group.project.named.settings.display.name", project.getName()),
        30, true);
    }
    add(40, projectGroup);
    SortedConfigurableGroup build = map.remove("build");
    if (build == null) {
      build = map.remove("build.tools");
    }
    else {
      build.add(1000, map.remove("build.tools"));
    }
    add(30, build);
    add(20, map.remove("language"));
    add(10, map.remove("tools"));
    add(-10, map.remove(null));
    // process unsupported groups
    if (1 < map.size()) {
      for (SortedConfigurableGroup group : map.values()) {
        if (this != group) {
          group.myDisplayName = OptionsBundle.message("configurable.group.category.named.settings.display.name", group.myGroupId);
          add(0, group);
        }
      }
    }
  }

  private void add(int weight, Configurable configurable) {
    if (configurable != null) {
      myList.add(new WeightConfigurable(configurable, weight));
    }
  }

  @Override
  protected Configurable[] buildConfigurables() {
    Collections.sort(myList);
    int length = myList.size();
    Configurable[] result = new Configurable[length];
    for (int i = 0; i < result.length; i++) {
      result[i] = myList.get(i).myConfigurable;
    }
    myList.clear();
    return result;
  }

  @NotNull
  @Override
  public String getId() {
    return "configurable.group." + myGroupId;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "configurable.group." + myGroupId + ".help.topic";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myDisplayName != null ? myDisplayName : OptionsBundle.message("configurable.group." + myGroupId + ".settings.display.name");
  }

  @Override
  public String getShortName() {
    return getDisplayName();
  }

  private static final class WeightConfigurable implements Comparable<WeightConfigurable> {
    private final Configurable myConfigurable;
    private final int myWeight;

    private WeightConfigurable(@NotNull Configurable configurable, int weight) {
      myConfigurable = configurable;
      myWeight = weight;
    }

    @Override
    public int compareTo(@NotNull WeightConfigurable pair) {
      return myWeight > pair.myWeight ? -1 :
             myWeight < pair.myWeight ? 1 :
             StringUtil.naturalCompare(myConfigurable.getDisplayName(), pair.myConfigurable.getDisplayName());
    }
  }
}
