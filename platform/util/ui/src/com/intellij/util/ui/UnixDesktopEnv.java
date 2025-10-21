// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import org.jetbrains.annotations.Nullable;

public enum UnixDesktopEnv {
  GNOME, KDE;

  public static @Nullable UnixDesktopEnv CURRENT = getDesktop();

  private static @Nullable UnixDesktopEnv getDesktop() {
    @SuppressWarnings({"SpellCheckingInspection", "RedundantSuppression"})
    String desktop = System.getenv("XDG_CURRENT_DESKTOP"), gdmSession = System.getenv("GDMSESSION");

    // http://askubuntu.com/questions/72549/how-to-determine-which-window-manager-is-running/227669#227669
    // https://userbase.kde.org/KDE_System_Administration/Environment_Variables#KDE_FULL_SESSION
    if (desktop != null && desktop.contains("GNOME")) return GNOME;
    if (gdmSession != null && gdmSession.contains("gnome")) return GNOME;
    if (desktop != null && desktop.contains("KDE")) return KDE;
    if (System.getenv("KDE_FULL_SESSION") != null) return KDE;
    return null;
  }
}
