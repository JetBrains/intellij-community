package com.intellij.xdebugger.settings;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

public abstract class XDebuggerSettingsManager {
  public static XDebuggerSettingsManager getInstance() {
    return ServiceManager.getService(XDebuggerSettingsManager.class);
  }

  public interface DataViewSettings {
    boolean isSortValues();

    boolean isAutoExpressions();

    int getValueLookupDelay();
  }

  @NotNull
  public abstract DataViewSettings getDataViewSettings();
}