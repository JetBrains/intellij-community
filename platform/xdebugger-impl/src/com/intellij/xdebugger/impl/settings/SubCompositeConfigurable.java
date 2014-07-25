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
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

abstract class SubCompositeConfigurable implements SearchableConfigurable.Parent {
  protected DataViewsConfigurableUi root;
  protected Configurable[] children;
  protected JComponent rootComponent;

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Override
  public boolean isVisible() {
    return true;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    getConfigurables();
    return children != null && children.length == 1 ? children[0].getHelpTopic() : null;
  }

  @Override
  public final void disposeUIResources() {
    root = null;
    rootComponent = null;

    if (isChildrenMerged()) {
      for (Configurable child : children) {
        child.disposeUIResources();
      }
    }
    children = null;
  }

  protected XDebuggerDataViewSettings getSettings() {
    return null;
  }

  @Nullable
  protected abstract DataViewsConfigurableUi createRootUi();

  @NotNull
  protected abstract DebuggerSettingsCategory getCategory();

  private boolean isChildrenMerged() {
    return children != null && children.length == 1;
  }

  @Override
  public final Configurable[] getConfigurables() {
    if (children == null) {
      List<Configurable> configurables = DebuggerConfigurable.getConfigurables(getCategory());
      children = configurables.toArray(new Configurable[configurables.size()]);
    }
    return isChildrenMerged() ? DebuggerConfigurable.EMPTY_CONFIGURABLES : children;
  }

  @Nullable
  @Override
  public final JComponent createComponent() {
    if (rootComponent == null) {
      if (root == null) {
        root = createRootUi();
      }

      getConfigurables();
      if (isChildrenMerged()) {
        if (children.length == 0) {
          rootComponent = root == null ? null : root.getComponent();
        }
        else if (root == null && children.length == 1) {
          rootComponent = children[0].createComponent();
        }
        else {
          JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
          if (root != null) {
            JComponent c = root.getComponent();
            c.setBorder(MergedCompositeConfigurable.BOTTOM_INSETS);
            panel.add(c);
          }
          for (Configurable configurable : children) {
            JComponent component = configurable.createComponent();
            if (component != null) {
              if (children[0] != configurable || !MergedCompositeConfigurable.isTargetedToProduct(configurable)) {
                component.setBorder(IdeBorderFactory.createTitledBorder(configurable.getDisplayName(), false));
              }
              panel.add(component);
            }
          }
          rootComponent = panel;
        }
      }
      else {
        rootComponent = root == null ? null : root.getComponent();
      }
    }
    return rootComponent;
  }

  @Override
  public final void reset() {
    if (root != null) {
      root.reset(getSettings());
    }

    if (isChildrenMerged()) {
      for (Configurable child : children) {
        child.reset();
      }
    }
  }

  @Override
  public final boolean isModified() {
    if (root != null && root.isModified(getSettings())) {
      return true;
    }
    else if (isChildrenMerged()) {
      for (Configurable child : children) {
        if (child.isModified()) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public final void apply() throws ConfigurationException {
    if (root != null) {
      root.apply(getSettings());
      for (DebuggerConfigurableProvider provider : DebuggerConfigurableProvider.EXTENSION_POINT.getExtensions()) {
        provider.generalApplied(getCategory());
      }
    }

    if (isChildrenMerged()) {
      for (Configurable child : children) {
        if (child.isModified()) {
          child.apply();
        }
      }
    }
  }
}