// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.impl.X11UiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Predicate;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class LinuxWindowManagerUsageCollector extends ApplicationUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("os.linux.wm", 6);

  private static final Map<String, String> GNOME_WINDOW_MANAGERS = new LinkedHashMap<>();
  private static final Map<String, String> WINDOW_MANAGERS = new LinkedHashMap<>();
  private static final List<String> ALL_NAMES = new ArrayList<>();

  private static final Map<String, String> SESSION_TYPES = new LinkedHashMap<>();
  private static final List<String> ALL_SESSION_NAMES = new ArrayList<>();

  @VisibleForTesting
  public static final String EMPTY_THEME = "empty";
  @VisibleForTesting
  public static final String UNKNOWN_THEME = "unknown";
  private static final List<String> GNOME_THEME_NAMES = Arrays.asList(
    "Adwaita",
    "Adwaita-dark",
    "Breeze",
    "Breeze-dark",
    "HighContrast",
    "HighContrastInverse",
    "Yaru",
    "Yaru-dark"
  );
  private static final List<String> GNOME_THEME_FAMILIES = Arrays.asList(
    "Yaru-"
  );
  private static final List<String> KDE_THEME_NAMES = Arrays.asList(
    "org.kde.breezedark.desktop",
    "org.kde.breezetwilight.desktop",
    "org.kde.breeze.desktop"
  );

  @VisibleForTesting
  public static final List<String> ALL_THEME_NAMES = new ArrayList<>();

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

    SESSION_TYPES.put("tty", "Terminal");
    SESSION_TYPES.put("x11", "X11");
    SESSION_TYPES.put("wayland", "Wayland");

    ALL_SESSION_NAMES.addAll(SESSION_TYPES.values());
    ALL_SESSION_NAMES.add("empty");
    ALL_SESSION_NAMES.add("Unknown");

    ALL_THEME_NAMES.add(EMPTY_THEME);
    ALL_THEME_NAMES.add(UNKNOWN_THEME);
    ALL_THEME_NAMES.addAll(GNOME_THEME_NAMES);
    for (String family : GNOME_THEME_FAMILIES) {
      ALL_THEME_NAMES.add(family + "*");
    }
    ALL_THEME_NAMES.addAll(KDE_THEME_NAMES);
  }

  private static final EventId1<String> CURRENT_DESKTOP =
    GROUP.registerEvent("xdg.current.desktop", EventFields.String("value", ALL_NAMES));

  private static final EventId1<String> SESSION_TYPE =
    GROUP.registerEvent("xdg.session.type", EventFields.String("value", ALL_SESSION_NAMES));

  private static final EventId1<String> THEME =
    GROUP.registerEvent("theme", EventFields.String("value", ALL_THEME_NAMES));

  private static final EventId1<String> ICON_THEME =
    GROUP.registerEvent("iconTheme", EventFields.String("value", ALL_THEME_NAMES));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
    if (SystemInfo.isLinux) {
      Set<MetricEvent> result = new HashSet<>();
      result.add(CURRENT_DESKTOP.metric(toReportedName(System.getenv("XDG_CURRENT_DESKTOP"))));
      result.add(SESSION_TYPE.metric(toReportedSessionName(System.getenv("XDG_SESSION_TYPE"))));
      result.add(THEME.metric(toReportedTheme(X11UiUtil.getTheme())));
      result.add(ICON_THEME.metric(toReportedTheme(X11UiUtil.getIconTheme())));
      return result;
    }
    return Collections.emptySet();
  }

  @VisibleForTesting
  public static @NotNull String toReportedSessionName(@Nullable String sessionType) {
    if (sessionType == null) {
      return "empty";
    }

    return SESSION_TYPES.getOrDefault(sessionType, "Unknown");
  }

  @VisibleForTesting
  public static @NotNull String toReportedTheme(@Nullable String theme) {
    if (theme == null) {
      return EMPTY_THEME;
    }

    String result = find(GNOME_THEME_NAMES, s -> s.equalsIgnoreCase(theme));
    if (result != null) {
      return result;
    }

    result = find(KDE_THEME_NAMES, s -> s.equalsIgnoreCase(theme));
    if (result != null) {
      return result;
    }

    result = find(GNOME_THEME_FAMILIES, s -> theme.startsWith(s));
    return result == null ? UNKNOWN_THEME : result + "*";
  }

  @VisibleForTesting
  public static @NotNull String toReportedName(@Nullable String windowManger) {
    if (windowManger == null) {
      return "empty";
    }

    windowManger = StringUtil.toLowerCase(windowManger);
    final boolean isGnome = windowManger.contains("gnome");
    return isGnome ? findReportedName(windowManger, GNOME_WINDOW_MANAGERS) : findReportedName(windowManger, WINDOW_MANAGERS);
  }

  private static @Nullable String find(@NotNull List<String> list, @NotNull Predicate<String> predicate) {
    return list.stream()
      .filter(predicate)
      .findFirst().orElse(null);
  }

  private static @NotNull String findReportedName(@NotNull String original, @NotNull Map<String, String> keywordToName) {
    for (Map.Entry<String, String> entry : keywordToName.entrySet()) {
      if (original.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return original;
  }
}
