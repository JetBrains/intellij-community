// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Konstantin Bulenkov
 */
public class LinuxWindowManagerUsageCollector extends ApplicationUsagesCollector implements AllowedDuringStartupCollector {
  private static class Lazy {
    private static final Map<String, String> GNOME_WINDOW_MANAGERS = new LinkedHashMap<>();
    private static final Map<String, String> WINDOW_MANAGERS = new LinkedHashMap<>();
    private static final List<String> ALL_NAMES = new ArrayList<>();

    static {
      GNOME_WINDOW_MANAGERS.put("shell", "Gnome Shell");
      GNOME_WINDOW_MANAGERS.put("ubuntu", "Ubuntu Gnome");
      GNOME_WINDOW_MANAGERS.put("budgie", "Budgie Gnome");
      GNOME_WINDOW_MANAGERS.put("classic", "Gnome Classic");
      GNOME_WINDOW_MANAGERS.put("flashback:unity", "GNOME Flashback Unity");
      GNOME_WINDOW_MANAGERS.put("flashback_unity", "GNOME Flashback Unity");
      GNOME_WINDOW_MANAGERS.put("flashback:gnome", "GNOME Flashback Gnome");
      GNOME_WINDOW_MANAGERS.put("flashback_gnome", "GNOME Flashback Gnome");
      GNOME_WINDOW_MANAGERS.put("flashback", "GNOME Flashback");
      GNOME_WINDOW_MANAGERS.put("pop", "pop_GNOME");
      GNOME_WINDOW_MANAGERS.put("awesome", "Awesome_GNOME");
      GNOME_WINDOW_MANAGERS.put("gnome", "Gnome");

      WINDOW_MANAGERS.put("unity7", "Unity7");
      WINDOW_MANAGERS.put("x-cinnamon", "X-Cinnamon");
      WINDOW_MANAGERS.put("xfce", "XFCE");
      WINDOW_MANAGERS.put("deepin", "Deepin");
      WINDOW_MANAGERS.put("unity", "Unity");
      WINDOW_MANAGERS.put("pantheon", "Pantheon");
      WINDOW_MANAGERS.put("i3", "i3");
      WINDOW_MANAGERS.put("kde", "KDE");
      WINDOW_MANAGERS.put("lxde", "LXDE");
      WINDOW_MANAGERS.put("mate", "MATE");
      WINDOW_MANAGERS.put("lxqt", "LXQt");
      WINDOW_MANAGERS.put("x-generic", "X-Generic");
      WINDOW_MANAGERS.put("icewm", "ICEWM");
      WINDOW_MANAGERS.put("ukui", "UKUI");
      WINDOW_MANAGERS.put("fluxbox", "Fluxbox");
      WINDOW_MANAGERS.put("lg3d", "LG3D");
      WINDOW_MANAGERS.put("enlightenment", "Enlightenment");
      WINDOW_MANAGERS.put("default.desktop", "default.desktop");

      ALL_NAMES.addAll(GNOME_WINDOW_MANAGERS.values());
      ALL_NAMES.addAll(WINDOW_MANAGERS.values());
    }

    private static final EventLogGroup GROUP = new EventLogGroup("os.linux.wm", 3);
    private static final EventId1<String> CURRENT_DESKTOP =
      GROUP.registerEvent("xdg.current.desktop", EventFields.String("value", ALL_NAMES));
  }

  @Override
  public EventLogGroup getGroup() {
    return Lazy.GROUP;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    if (SystemInfo.isLinux) {
      Set<MetricEvent> result = new HashSet<>();
      result.add(Lazy.CURRENT_DESKTOP.metric(toReportedName(System.getenv("XDG_CURRENT_DESKTOP"))));
      return result;
    }
    return Collections.emptySet();
  }

  @VisibleForTesting
  @NotNull
  public static String toReportedName(@Nullable String windowManger) {
    if (windowManger == null) {
      return "empty";
    }

    windowManger = StringUtil.toLowerCase(windowManger);
    final boolean isGnome = windowManger.contains("gnome");
    return isGnome ? findReportedName(windowManger, Lazy.GNOME_WINDOW_MANAGERS) : findReportedName(windowManger, Lazy.WINDOW_MANAGERS);
  }

  @NotNull
  private static String findReportedName(@NotNull String original, @NotNull Map<String, String> keywordToName) {
    for (Map.Entry<String, String> entry : keywordToName.entrySet()) {
      if (original.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return original;
  }
}
