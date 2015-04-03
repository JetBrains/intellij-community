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
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Sergey.Malenkov
 */
public final class SortedConfigurableGroup
  extends SearchableConfigurable.Parent.Abstract
  implements SearchableConfigurable, Weighted, ConfigurableGroup, Configurable.NoScroll {

  private ArrayList<Configurable> myList = new ArrayList<Configurable>();
  private final String myGroupId;
  private String myDisplayName;
  private int myWeight;

  private SortedConfigurableGroup(String groupId) {
    myGroupId = groupId;
  }

  public SortedConfigurableGroup(Project project, Map<String, List<Configurable>> map) {
    myGroupId = "root";
    // create groups from configurations
    List<Configurable> list = map.remove(myGroupId);
    if (list != null) {
      myList.addAll(list);
    }
    // process supported groups
    add(70, remove(map, "appearance"));
    add(60, remove(map, "editor"));
    SortedConfigurableGroup projectGroup = remove(map, "project");
    if (projectGroup != null && project != null && !project.isDefault()) {
      projectGroup.myDisplayName = StringUtil.first(
        OptionsBundle.message("configurable.group.project.named.settings.display.name", project.getName()),
        30, true);
    }
    add(40, projectGroup);
    SortedConfigurableGroup build = remove(map, "build");
    if (build == null) {
      build = remove(map, "build.tools");
    }
    else {
      build.add(1000, remove(map, "build.tools"));
    }
    add(30, build);
    add(20, remove(map, "language"));
    add(10, remove(map, "tools"));
    add(-10, remove(map, null));
    // process unsupported groups
    if (0 < map.size()) {
      for (String groupId : map.keySet().toArray(new String[map.size()])) {
        SortedConfigurableGroup group = remove(map, groupId);
        if (group != null) {
          group.myDisplayName = OptionsBundle.message("configurable.group.category.named.settings.display.name", groupId);
          add(0, group);
        }
      }
    }
  }

  private static SortedConfigurableGroup remove(Map<String, List<Configurable>> map, String groupId) {
    List<Configurable> list = map.remove(groupId);
    if (list == null) {
      return null;
    }
    SortedConfigurableGroup group = new SortedConfigurableGroup(groupId);
    group.myList.addAll(list);
    return group;
  }

  private void add(int weight, SortedConfigurableGroup configurable) {
    if (configurable != null) {
      configurable.myWeight = weight;
      myList.add(configurable);
    }
  }

  @Override
  protected Configurable[] buildConfigurables() {
    Collections.sort(myList, COMPARATOR);
    Configurable[] result = ArrayUtil.toObjectArray(myList, Configurable.class);
    myList.clear();
    myList = null;
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

  @Override
  public int getWeight() {
    return myWeight;
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
}
