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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

public final class MixedConfigurableGroup implements ConfigurableGroup {
  private final String myGroupId;
  private Configurable[] myConfigurables;

  private MixedConfigurableGroup(String groupId, ArrayList<Configurable> configurables) {
    myGroupId = groupId;
    myConfigurables = (configurables != null)
                      ? configurables.toArray(new Configurable[configurables.size()])
                      : new Configurable[0];
  }

  private MixedConfigurableGroup(String groupId, HashMap<String, ArrayList<Configurable>> configurables) {
    this(groupId, configurables.remove(groupId));
  }

  @Override
  public String getDisplayName() {
    return OptionsBundle.message("configurable.group." + myGroupId + ".settings.display.name");
  }

  @Override
  public String getShortName() {
    return getDisplayName();
  }

  @Override
  public Configurable[] getConfigurables() {
    return myConfigurables;
  }

  public static ConfigurableGroup[] getGroups(Configurable... configurables) {
    HashMap<String, ArrayList<Configurable>> map = new HashMap<String, ArrayList<Configurable>>();
    for (Configurable configurable : configurables) {
      String groupId = null;
      if (configurable instanceof ConfigurableWrapper) {
        groupId = ((ConfigurableWrapper)configurable).getGroupId();
      }
      ArrayList<Configurable> list = map.get(groupId);
      if (list == null) {
        map.put(groupId, list = new ArrayList<Configurable>());
      }
      list.add(configurable);
    }
    ArrayList<ConfigurableGroup> groups = new ArrayList<ConfigurableGroup>(map.size());
    groups.add(new MixedConfigurableGroup("appearance", map));
    groups.add(new MixedConfigurableGroup("editor", map));
    groups.add(new MixedConfigurableGroup("project", map));
    groups.add(new MixedConfigurableGroup("build", map));
    groups.add(new MixedConfigurableGroup("language", map));
    groups.add(new MixedConfigurableGroup("tools", map));
    ConfigurableGroup other = new MixedConfigurableGroup(null, map);
    for(Entry<String, ArrayList<Configurable>>entry: map.entrySet()){
      groups.add(new MixedConfigurableGroup(entry.getKey(), entry.getValue()));
    }
    groups.add(other);
    return groups.toArray(new ConfigurableGroup[groups.size()]);
  }
}
