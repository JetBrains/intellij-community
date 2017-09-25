/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.statistic.customUsageCollectors.actions;

import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "MainMenuCollector",
  storages = @Storage(value = "statistics.main_menu.xml", roamingType = RoamingType.DISABLED)
)
public class MainMenuCollector implements PersistentStateComponent<MainMenuCollector.State> {
  private State myState = new State();
  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;
  }

  public void record(@NotNull AnAction action) {
    try {
      AWTEvent e = EventQueue.getCurrentEvent();
      String path = null;
      if (e instanceof ItemEvent) {
        path = getPathFromMenuItem(e, action);
      }
      else if (e instanceof MouseEvent) {
        path = getPathFromMenuSelectionManager(action);
      }

      if (!StringUtil.isEmpty(path)) {
        String key = ConvertUsagesUtil.escapeDescriptorName(path);
        final Integer count = myState.myValues.get(key);
        int value = count == null ? 1 : count + 1;
        myState.myValues.put(key, value);
      }
    }
    catch (Exception ignore) {
    }
  }

  protected String getPathFromMenuSelectionManager(@NotNull AnAction action) {
    List<String> groups = Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath())
      .filter(o -> o instanceof ActionMenu)
      .map(o -> ((ActionMenu)o).getText())
      .collect(Collectors.toList());
    if (groups.size() > 0) {
      String text = action.getTemplatePresentation().getText(); //avoid user data in Action Presentation
      groups.add(text);
      return convertMenuItemsToKey(groups);
    }
    return null;
  }

  @NotNull
  private static String convertMenuItemsToKey(List<String> menuItems) {
    return StringUtil.join(menuItems, " -> ");
  }

  @NotNull
  protected String getPathFromMenuItem(AWTEvent e, AnAction action) {
    Object src = e.getSource();
    ArrayList<String> items = new ArrayList<>();
    while (src instanceof MenuItem) {
      items.add (0, ((MenuItem)src).getLabel());
      src = ((MenuItem)src).getParent();
    }
    if (items.size() > 1) {
      items.set(items.size() - 1, action.getTemplatePresentation().getText()); //avoid user data in Action Presentation
    }
    return convertMenuItemsToKey(items);
  }


  public static MainMenuCollector getInstance() {
    return ServiceManager.getService(MainMenuCollector.class);
  }


  final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "path", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }

  final static class MainMenuUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create("Main Menu", GroupDescriptor.HIGHER_PRIORITY);

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      State state = getInstance().getState();
      assert state != null;
      return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
}
