// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.actions.persistence;

import com.intellij.internal.statistic.utils.PluginInfo;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.impl.BundledKeymapProvider;
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

class ActionsBuiltInWhitelist {
  private static final ActionsBuiltInWhitelist ourInstance = new ActionsBuiltInWhitelist();

  static ActionsBuiltInWhitelist getInstance() {
    return ourInstance;
  }

  private final Set<String> ourXmlActionIds = new HashSet<>();
  private final Set<String> ourCustomActionWhitelist = ContainerUtil.newHashSet(
    "regexp.help", "ShowUsagesPopup.showSettings",
    "Reload Classes", "DialogCancelAction", "DialogOkAction", "DoubleShortcut"
  );

  private ActionsBuiltInWhitelist() {
  }

  public boolean isCustomAllowedAction(@NotNull String actionId) {
    return ourCustomActionWhitelist.contains(actionId);
  }

  public boolean isWhitelistedActionId(@NotNull String actionId) {
    return isCustomAllowedAction(actionId) || ourXmlActionIds.contains(actionId);
  }

  public void addActionLoadedFromXml(@NotNull String actionId, @Nullable PluginId pluginId) {
    PluginInfo pluginInfo = PluginInfoDetectorKt.getPluginInfoById(pluginId);
    if (pluginInfo.isSafeToReport()) {
      ourXmlActionIds.add(actionId);
    }
  }

  public void addActionsLoadedFromKeymapXml(@NotNull Keymap keymap, @NotNull Set<String> actionIds) {
    if (keymap instanceof DefaultKeymapImpl) {
      Class<BundledKeymapProvider> provider = ((DefaultKeymapImpl)keymap).getProviderClass();
      if (PluginInfoDetectorKt.getPluginInfo(provider).isDevelopedByJetBrains()) {
        ourXmlActionIds.addAll(actionIds);
      }
    }
  }
}
