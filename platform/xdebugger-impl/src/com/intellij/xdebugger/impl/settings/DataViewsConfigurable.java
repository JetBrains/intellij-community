/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class DataViewsConfigurable implements SearchableConfigurable.Parent {
  private DataViewsConfigurableUi root;
  private Configurable[] children;

  private JComponent component;

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
    return children.length == 1 ? DebuggerConfigurable.EMPTY_CONFIGURABLES : children;
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
    getConfigurables();
    return children.length == 1 ? children[0].getHelpTopic() : null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (component == null) {
      if (root == null) {
        root = new DataViewsConfigurableUi();
      }

      getConfigurables();
      if (children.length == 1) {
        JPanel panel = new JPanel(new BorderLayout(0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET));
        panel.add(root.getComponent(), BorderLayout.NORTH);
        Configurable configurable = children[0];
        JComponent configurableComponent = configurable.createComponent();
        assert configurableComponent != null;
        configurableComponent.setBorder(IdeBorderFactory.createTitledBorder(configurable.getDisplayName(), false));
        panel.add(configurableComponent, BorderLayout.CENTER);
        component = panel;
      }
      else {
        component = root.getComponent();
      }
    }
    return component;
  }

  @Override
  public boolean isModified() {
    if (root != null && root.isModified(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings())) {
      return true;
    }
    else {
      return children.length == 1 && children[0].isModified();
    }
  }

  @Override
  public void apply() throws ConfigurationException {
    if (root != null) {
      root.apply(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings());
      for (DebuggerSupport support : DebuggerSupport.getDebuggerSupports()) {
        support.getSettingsPanelProvider().applied(XDebuggerSettings.Category.DATA_VIEWS);
      }
    }

    if (children.length == 1) {
      children[0].apply();
    }
  }

  @Override
  public void reset() {
    if (root != null) {
      root.reset(XDebuggerSettingsManager.getInstanceImpl().getDataViewSettings());
    }
    if (children.length == 1) {
      children[0].reset();
    }
  }

  @Override
  public void disposeUIResources() {
    root = null;
    component = null;

    if (children.length == 1) {
      children[0].disposeUIResources();
    }
    children = null;
  }
}