// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum UnixDesktopEnv {
  BUDGIE("Budgie:GNOME", "budgie-desktop"),
  CINNAMON("X-Cinnamon", "cinnamon"),
  DEEPIN("Deepin", "dde-desktop"),
  GNOME("GNOME", "gnome-shell"),
  @SuppressWarnings("SpellCheckingInspection")
  HYPRLAND("Hyprland", "hyprctl", "version"),
  @SuppressWarnings("SpellCheckingInspection")
  KDE("KDE", "plasmashell"),
  @SuppressWarnings("SpellCheckingInspection")
  LXDE("LXDE", "lxsession"),
  @SuppressWarnings("SpellCheckingInspection")
  LXQT("LXQt", "lxqt-session"),
  MATE("MATE", "mate-session"),
  PANTHEON("Pantheon", "gala"),
  UNITY("Unity", "unity"),
  @SuppressWarnings("SpellCheckingInspection")
  XFCE("XFCE", "xfce4-session"),
  I3("i3", "i3"),
  SWAY("sway", "sway");

  public static final @Nullable UnixDesktopEnv CURRENT = getDesktop();

  private final String myXdgDesktopSubstring;
  private final String myVersionTool, myVersionArg;

  UnixDesktopEnv(String xdgDesktopSubstring, String versionTool) {
    this(xdgDesktopSubstring, versionTool, "--version");
  }

  UnixDesktopEnv(String xdgDesktopSubstring, String versionTool, String versionArg) {
    myXdgDesktopSubstring = xdgDesktopSubstring;
    myVersionTool = versionTool;
    myVersionArg = versionArg;
  }

  @ApiStatus.Internal
  public @NotNull List<String> getVersionCommand() {
    return List.of(myVersionTool, myVersionArg);
  }

  private static @Nullable UnixDesktopEnv getDesktop() {
    @SuppressWarnings({"SpellCheckingInspection", "RedundantSuppression"})
    String desktop = System.getenv("XDG_CURRENT_DESKTOP"), gdmSession = System.getenv("GDMSESSION");

    var knownEnvironments = new ArrayList<>(List.of(values()));
    // sort by descending length of substring, so that longer substrings are checked first, because we have {@code GNOME} and {@code Budgie:GNOME}
    knownEnvironments.sort(Comparator.comparing(it -> -it.myXdgDesktopSubstring.length()));
    for (var env : knownEnvironments) {
      if (desktop != null && desktop.contains(env.myXdgDesktopSubstring)) return env;
    }

    // http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running/227669#227669
    // https://userbase.kde.org/KDE_System_Administration/Environment_Variables#KDE_FULL_SESSION
    if (gdmSession != null && gdmSession.contains("gnome")) return GNOME;
    if (System.getenv("KDE_FULL_SESSION") != null) return KDE;
    return null;
  }
}
