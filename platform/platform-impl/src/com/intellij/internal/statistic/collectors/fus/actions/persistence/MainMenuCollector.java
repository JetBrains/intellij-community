// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@Service
public final class MainMenuCollector {
  public static MainMenuCollector getInstance() {
    return ApplicationManager.getApplication().getService(MainMenuCollector.class);
  }

  public void record(@NotNull AnAction action) {
    try {
      final PluginInfo info = PluginInfoDetectorKt.getPluginInfo(action.getClass());
      if (!info.isDevelopedByJetBrains()) {
        return;
      }

      AWTEvent e = EventQueue.getCurrentEvent();
      String path = null;

      if (e instanceof ItemEvent) {
        path = getPathFromMenuItem(e, action);
      }
      else if (e instanceof MouseEvent) {
        path = getPathFromMenuSelectionManager(action);
      }
    }
    catch (Exception ignore) {
    }
  }

  private String getPathFromMenuSelectionManager(@NotNull AnAction action) {
    List<String> groups = Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath())
      .filter(o -> o instanceof ActionMenu)
      .map(o -> ((ActionMenu)o).getAnAction().getTemplateText())
      .collect(Collectors.toList());
    if (groups.size() > 0) {
      String text = getActionText(action);
      groups.add(text);
      return convertMenuItemsToKey(groups);
    }
    return null;
  }

  private static String getActionText(@NotNull AnAction action) {
    final String actionId = ActionManager.getInstance().getId(action);
    if (StringUtil.isEmpty(actionId)) {
      return "generated.on.runtime";
    }
    return action.getTemplateText(); //avoid user data in Action Presentation
  }

  @NotNull
  private static String convertMenuItemsToKey(List<String> menuItems) {
    return StringUtil.join(menuItems, " -> ");
  }

  @NotNull
  private String getPathFromMenuItem(AWTEvent e, AnAction action) {
    Object src = e.getSource();
    ArrayList<String> items = new ArrayList<>();
    while (src instanceof MenuItem) {
      items.add(0, ((MenuItem)src).getLabel());
      src = ((MenuItem)src).getParent();
    }
    if (items.size() > 1) {
      items.set(items.size() - 1, getActionText(action));
    }
    return convertMenuItemsToKey(items);
  }

  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "path", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
