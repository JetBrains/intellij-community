package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

class XDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
  @NotNull
  @Override
  public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
    List<Configurable> list;
    if (category == DebuggerSettingsCategory.GENERAL) {
      list = new SmartList<Configurable>(SimpleConfigurable.create("debugger.general", "", GeneralConfigurableUi.class, new Getter<XDebuggerGeneralSettings>() {
        @Override
        public XDebuggerGeneralSettings get() {
          return XDebuggerSettingsManager.getInstanceImpl().getGeneralSettings();
        }
      }));
    }
    else {
      list = null;
    }

    for (XDebuggerSettings<?> settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      Collection<? extends Configurable> configurables = settings.createConfigurables(category);
      if (!configurables.isEmpty()) {
        if (list == null) {
          list = new SmartList<Configurable>();
        }
        list.addAll(configurables);
      }
    }

    if (category == DebuggerSettingsCategory.ROOT) {
      for (XDebuggerSettings settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
        @SuppressWarnings("deprecation")
        Configurable configurable = settings.createConfigurable();
        if (configurable != null) {
          if (list == null) {
            list = new SmartList<Configurable>();
          }
          list.add(configurable);
        }
      }
    }
    return ContainerUtil.notNullize(list);
  }

  @Override
  public void generalApplied(@NotNull DebuggerSettingsCategory category) {
    for (XDebuggerSettings<?> settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      settings.generalApplied(category);
    }
  }

  @Override
  public boolean isTargetedToProduct(@NotNull Configurable configurable) {
    for (XDebuggerSettings<?> settings : XDebuggerSettingsManager.getInstanceImpl().getSettingsList()) {
      if (settings.isTargetedToProduct(configurable)) {
        return true;
      }
    }
    return super.isTargetedToProduct(configurable);
  }
}