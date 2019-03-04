// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.os;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Konstantin Bulenkov
 */
public class LinuxWindowManagerUsageCollector extends ApplicationUsagesCollector {
  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    if (SystemInfo.isLinux) {
      String wmName = System.getenv("XDG_CURRENT_DESKTOP");

      return Collections.singleton(new UsageDescriptor(clearUserData(wmName)));
    }

    return Collections.emptySet();
  }

  static class Lazy {
    private static final Map<String, String> WINDOW_MANAGERS = new HashMap<>();
    static {
      WINDOW_MANAGERS.put("ubuntu_GNOME", "Ubuntu Gnome");
      WINDOW_MANAGERS.put("ubuntu-communitheme_ubuntu_GNOME", "Ubuntu Gnome");
      WINDOW_MANAGERS.put("communitheme_ubuntu_GNOME", "Ubuntu Gnome");
      WINDOW_MANAGERS.put("THEMENAME_ubuntu_GNOME", "Ubuntu Gnome");
      WINDOW_MANAGERS.put("gnome", "Gnome");
      WINDOW_MANAGERS.put("GNOME", "Gnome");
      WINDOW_MANAGERS.put("Gnome", "Gnome");
      WINDOW_MANAGERS.put("GNOME-Classic_GNOME", "Gnome");
      WINDOW_MANAGERS.put("Budgie_GNOME", "Budgie Gnome");
      WINDOW_MANAGERS.put("X-Budgie_GNOME", "Budgie Gnome");
      WINDOW_MANAGERS.put("GNOME-Flashback_Unity", "GNOME Flashback Unity");
      WINDOW_MANAGERS.put("GNOME-Flashback_GNOME", "GNOME Flashback Gnome");
      WINDOW_MANAGERS.put("pop_GNOME", "pop_GNOME");
      WINDOW_MANAGERS.put("Awesome_GNOME", "Awesome_GNOME");

      WINDOW_MANAGERS.put("X-Cinnamon", "X-Cinnamon");
      WINDOW_MANAGERS.put("XFCE", "XFCE");
      WINDOW_MANAGERS.put("Deepin", "Deepin");
      WINDOW_MANAGERS.put("Unity", "Unity");
      WINDOW_MANAGERS.put("Pantheon", "Pantheon");
      WINDOW_MANAGERS.put("i3", "i3");
      WINDOW_MANAGERS.put("KDE", "KDE");
      WINDOW_MANAGERS.put("LXDE", "LXDE");
      WINDOW_MANAGERS.put("MATE", "MATE");

      WINDOW_MANAGERS.put("Unity_Unity7_ubuntu", "Unity7");
      WINDOW_MANAGERS.put("Unity_Unity7", "Unity7");

      WINDOW_MANAGERS.put("LXQt", "LXQt");
      WINDOW_MANAGERS.put("X-LXQt", "LXQt");

      WINDOW_MANAGERS.put("X-Generic", "X-Generic");
      WINDOW_MANAGERS.put("ICEWM", "ICEWM");
      WINDOW_MANAGERS.put("UKUI", "UKUI");
      WINDOW_MANAGERS.put("Fluxbox", "Fluxbox");
      WINDOW_MANAGERS.put("Enlightenment", "Enlightenment");
      WINDOW_MANAGERS.put("default.desktop", "default.desktop");
    }
  }
  private static String clearUserData(String windowManger) {
    if (windowManger == null) {
      return "XDG_CURRENT_DESKTOP is empty";
    }

    String value = Lazy.WINDOW_MANAGERS.get(windowManger);
    return StringUtil.isEmpty(value) ? "Unknown" : value;
  }

  @NotNull
  @Override
  public String getGroupId() { return "os.linux.wm"; }

  @Nullable
  @Override
  public FeatureUsageData getData() {
    return new FeatureUsageData().addOS();
  }
}
