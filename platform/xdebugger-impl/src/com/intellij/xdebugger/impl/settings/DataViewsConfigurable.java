package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class DataViewsConfigurable implements SearchableConfigurable.Parent {
  private DataViewsConfigurableUi root;
  private Configurable[] children;

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Override
  public Configurable[] getConfigurables() {
    if (children == null) {
      List<Configurable> configurables = DebuggerConfigurableProvider.getConfigurables(XDebuggerSettings.Category.DATA_VIEWS);
      children = configurables.toArray(new Configurable[configurables.size()]);
    }
    return children;
  }

  @NotNull
  @Override
  public String getId() {
    return "debugger.dataViews";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return XDebuggerBundle.message("debugger.dataViews.display.name");
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (root == null) {
      root = new DataViewsConfigurableUi();
    }
    return root.getComponent();
  }

  @Override
  public boolean isModified() {
    return root != null && root.isModified(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings());
  }

  @Override
  public void apply() throws ConfigurationException {
    if (root != null) {
      root.apply(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings());
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        support.getSettingsPanelProvider().applied(XDebuggerSettings.Category.DATA_VIEWS);
      }
    }
  }

  @Override
  public void reset() {
    if (root != null) {
      root.reset(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings());
    }
  }

  @Override
  public void disposeUIResources() {
    root = null;
    children = null;
  }
}