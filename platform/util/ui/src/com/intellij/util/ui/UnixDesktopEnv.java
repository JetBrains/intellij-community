// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum UnixDesktopEnv {
  Budgie("Budgie:GNOME", "budgie-desktop"),
  Cinnamon("X-Cinnamon", "cinnamon"),
  Deepin("Deepin", "dde-desktop"),
  GNOME("GNOME", "gnome-shell"),
  Hyprland("Hyprland", "hyprctl", List.of("version")),
  KDE("KDE", "plasmashell"),
  LXDE("LXDE", "lxsession"),
  LXQT("LXQt", "lxqt-session"),
  MATE("MATE", "mate-session"),
  Pantheon("Pantheon", "gala"),
  Unity("Unity", "unity"),
  XFCE("XFCE", "xfce4-session"),
  i3("i3", "i3"),
  sway("sway", "sway");

  public static final @Nullable UnixDesktopEnv CURRENT = getDesktop();

  private final @NotNull String myXdgDesktopSubstring;
  private final @NotNull String myVersionTool;
  private final @NotNull List<String> myVersionToolArguments;

  UnixDesktopEnv(@NotNull String xdgDesktopSubstring, @NotNull String versionTool, @NotNull List<String> versionToolArguments) {
    myXdgDesktopSubstring = xdgDesktopSubstring;
    myVersionTool = versionTool;
    myVersionToolArguments = versionToolArguments;
  }

  UnixDesktopEnv(@NotNull String xdgDesktopSubstring, @NotNull String versionTool) {
    this(xdgDesktopSubstring, versionTool, List.of("--version"));
  }

  @ApiStatus.Internal
  public @NotNull String getVersionTool() {
    return myVersionTool;
  }

  @ApiStatus.Internal
  public @NotNull List<String> getVersionToolArguments() {
    return myVersionToolArguments;
  }

  private static @Nullable UnixDesktopEnv getDesktop() {
    @SuppressWarnings({"SpellCheckingInspection", "RedundantSuppression"})
    String desktop = System.getenv("XDG_CURRENT_DESKTOP"), gdmSession = System.getenv("GDMSESSION");

    var knownEnvironments = new ArrayList<>(List.of(values()));
    // sort by descending length of substring, so that longer substrings are checked first, because we have {@code GNOME} and {@code Budgie:GNOME}
    knownEnvironments.sort(Comparator.comparing(it -> -it.myXdgDesktopSubstring.length()));
    for (UnixDesktopEnv env : knownEnvironments) {
      if (desktop != null && desktop.contains(env.myXdgDesktopSubstring)) return env;
    }

    // http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running/227669#227669
    // https://userbase.kde.org/KDE_System_Administration/Environment_Variables#KDE_FULL_SESSION
    if (gdmSession != null && gdmSession.contains("gnome")) return GNOME;
    if (System.getenv("KDE_FULL_SESSION") != null) return KDE;
    return null;
  }
}
