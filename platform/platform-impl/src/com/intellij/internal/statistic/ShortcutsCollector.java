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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.components.*;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "ToolbarClicksCollector",
  storages = @Storage(value = "statistics.shortcuts.xml", roamingType = RoamingType.DISABLED)
)
public class ShortcutsCollector implements PersistentStateComponent<ShortcutsCollector.MyState> {
  final static class MyState {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "shortcut", valueAttributeName = "count")
    public final Map<String, Integer> myValues = new HashMap<>();
  }
  private MyState myState = new MyState();

  @NotNull
  public MyState getState() {
    return myState;
  }

  public void loadState(final MyState state) {
    myState = state;
  }

  public static void record(AnActionEvent event) {
    InputEvent e = event.getInputEvent();
    if (e instanceof KeyEvent) {
      KeyboardShortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent((KeyEvent)e), null);
      String key = KeymapUtil.getShortcutText(shortcut);
      ShortcutsCollector collector = getInstance();

      if (collector == null) return; //no shortcuts stats for the IDE

      Map<String, Integer> values = collector.getState().myValues;
      values.put(key, ContainerUtil.getOrElse(values, key, 0) + 1);
    }
  }


  private static ShortcutsCollector getInstance() {
    return ServiceManager.getService(ShortcutsCollector.class);
  }

  final static class ShortcutUsagesCollector extends UsagesCollector {
    private static final GroupDescriptor GROUP = GroupDescriptor.create(getGroupName(), GroupDescriptor.HIGHER_PRIORITY);

    private static String getGroupName() {
      if (SystemInfo.isMac) return "Shortcuts on Mac";
      if (SystemInfo.isWindows) return "Shortcuts on Windows";
      if (SystemInfo.isLinux) return "Shortcuts on Linux";
      return "Shortcuts on OtherOs";
    }

    @NotNull
    public Set<UsageDescriptor> getUsages() {
      MyState state = getInstance().getState();
      assert state != null;
      return ContainerUtil.map2Set(state.myValues.entrySet(), e -> new UsageDescriptor(e.getKey(), e.getValue()));
    }

    @NotNull
    public GroupDescriptor getGroupId() {
      return GROUP;
    }
  }
}
