// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Konstantin Bulenkov
 */
@State(
  name = "MainMenuCollector",
  storages = {
    @Storage(value = UsageStatisticsPersistenceComponent.USAGE_STATISTICS_XML, roamingType = RoamingType.DISABLED),
    @Storage(value = "statistics.main_menu.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  }
)
public class MainMenuCollector extends BaseUICollector implements PersistentStateComponent<MainMenuCollector.State> {
  private State myState = new State();
  @Nullable
  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  public void record(@NotNull AnAction action) {
    try {
      if (isNotBundledPluginClass(action.getClass())) {
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


  private static Pair<Double, Double> findBucket(double value, double... ranges) {
    if (ranges.length == 0) throw new IllegalArgumentException("Constrains are empty");
    if (value < ranges[0]) return Pair.create(null, ranges[0]);
    for (int i = 1; i < ranges.length; i++) {
      if (ranges[i] <= ranges[i - 1])
        throw new IllegalArgumentException("Constrains are unsorted");

      if (value < ranges[i]) {
        return Pair.create(ranges[i - 1], ranges[i]);
      }
    }

    return Pair.create(ranges[ranges.length - 1], null);
  }



  protected static String findBucket(long value, Function<Long, String> valueConverter, long...ranges) {
    double[] dRanges = new double[ranges.length];
    for (int i = 0; i < dRanges.length; i++) {
      dRanges[i] = ranges[i];
    }
    return findBucket((double)value, (d) -> valueConverter.apply(d.longValue()), dRanges);
  }

  protected static String findBucket(double value, Function<Double, String> valueConverter, double...ranges) {
    for (double range : ranges) {
      if (range == value) {
        return valueConverter.apply(value);
      }
    }

    Pair<Double, Double> bucket = findBucket(value, ranges);
    if (bucket.first == null) return "(*, " + valueConverter.apply(bucket.second) + ")";
    if (bucket.second == null) return "(" + valueConverter.apply(bucket.first) + ", *)";
    return "(" + valueConverter.apply(bucket.first) + ", " + valueConverter.apply(bucket.second) + ")";
  }

  protected String getPathFromMenuSelectionManager(@NotNull AnAction action) {
    List<String> groups = Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath())
      .filter(o -> o instanceof ActionMenu)
      .map(o -> ((ActionMenu)o).getText())
      .collect(Collectors.toList());
    if (groups.size() > 0) {
      String text = getActionText(action);
      groups.add(text);
      return convertMenuItemsToKey(groups);
    }
    return null;
  }

  private static final HashMap<String, String> ourBlackList = new HashMap<>();
  static {
    ourBlackList.put("com.intellij.ide.ReopenProjectAction", "Reopen Project");
  }

  private static String getActionText(@NotNull AnAction action) {
    String text = ourBlackList.get(action.getClass().getName());
    if (text != null) {
      return text;
    }
    return action.getTemplatePresentation().getText(); //avoid user data in Action Presentation
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
      items.set(items.size() - 1, getActionText(action));
    }
    return convertMenuItemsToKey(items);
  }


  public static MainMenuCollector getInstance() {
    return ServiceManager.getService(MainMenuCollector.class);
  }


  public final static class State {
    @Tag("counts")
    @MapAnnotation(surroundWithTag = false, keyAttributeName = "path", valueAttributeName = "count")
    public Map<String, Integer> myValues = new HashMap<>();
  }
}
