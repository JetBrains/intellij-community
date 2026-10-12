// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class ActionsBuiltInAllowedlist {
  private static final ActionsBuiltInAllowedlist ourInstance = new ActionsBuiltInAllowedlist();

  static ActionsBuiltInAllowedlist getInstance() {
    return ourInstance;
  }

  private final Object myLock = new Object();
  private final Map<AnAction, String> myDynamicActionsToId = new WeakHashMap<>();

  // guarded by myLock
  private @Nullable Reference<IdeaPluginDescriptor> myLastPlugin;
  private boolean myLastPluginSafeToReport;

  private final Set<String> ourXmlActionIds = new HashSet<>();
  private final Set<String> ourCustomActions = ContainerUtil.newHashSet(
    "ShowUsagesPopup.showSettings",
    "Reload Classes", "DialogCancelAction", "DialogOkAction", "DoubleShortcut"
  );

  private ActionsBuiltInAllowedlist() {
  }

  public boolean isCustomAllowedAction(@NotNull String actionId) {
    return ourCustomActions.contains(actionId);
  }

  public boolean isAllowedActionId(@NotNull String actionId) {
    return isCustomAllowedAction(actionId) || isStaticXmlActionId(actionId);
  }

  private boolean isStaticXmlActionId(@NotNull String actionId) {
    synchronized (myLock) {
      return ourXmlActionIds.contains(actionId);
    }
  }

  public void addActionLoadedFromXml(@NotNull String actionId, @Nullable IdeaPluginDescriptor plugin) {
    if (plugin != null && isSafeToReport(plugin)) {
      synchronized (myLock) {
        ourXmlActionIds.add(actionId);
      }
    }
  }

  /**
   * The actions of a plugin are registered one after another, so the plugin info is computed once per plugin, not once per action.
   * The reference is weak: an unloaded plugin must not stay reachable from here.
   */
  private boolean isSafeToReport(@NotNull IdeaPluginDescriptor plugin) {
    synchronized (myLock) {
      if (myLastPlugin != null && myLastPlugin.get() == plugin) {
        return myLastPluginSafeToReport;
      }
    }

    boolean isSafeToReport = PluginInfoDetectorKt.getPluginInfoByDescriptor(plugin).isSafeToReport();
    synchronized (myLock) {
      myLastPlugin = new WeakReference<>(plugin);
      myLastPluginSafeToReport = isSafeToReport;
    }
    return isSafeToReport;
  }

  public void addActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    if (keymap instanceof DefaultKeymapImpl) {
      PluginDescriptor plugin = ((DefaultKeymapImpl)keymap).getPlugin();
      if (PluginInfoDetectorKt.getPluginInfoByDescriptor(plugin).isDevelopedByJetBrains()) {
        synchronized (myLock) {
          ourXmlActionIds.addAll(actionIds);
        }
      }
    }
  }

  public void registerDynamicActionId(@NotNull AnAction action, @NotNull String id) {
    synchronized (myLock) {
      if (isAllowedActionId(id)) {
        myDynamicActionsToId.put(action, id);
      }
    }
  }

  public @Nullable String getDynamicActionId(@NotNull AnAction action) {
    synchronized (myLock) {
      return myDynamicActionsToId.get(action);
    }
  }
}
