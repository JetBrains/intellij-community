// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import java.util.Arrays;
import java.util.List;

abstract class SubCompositeConfigurable implements SearchableConfigurable.Parent, SearchableConfigurable.Merged {
  protected DataViewsConfigurableUi root;
  protected Configurable[] children;
  protected JComponent rootComponent;

  @Override
  public boolean hasOwnContent() {
    return true;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    buildConfigurables();
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
  public final Configurable @NotNull [] getConfigurables() {
    buildConfigurables();
    return isChildrenMerged() ? DebuggerConfigurable.EMPTY_CONFIGURABLES : children;
  }

  @NotNull
  @Override
  public final List<Configurable> getMergedConfigurables() {
    buildConfigurables();
    return isChildrenMerged()
           ? Arrays.asList(children)
           : Arrays.asList(DebuggerConfigurable.EMPTY_CONFIGURABLES);
  }

  private void buildConfigurables() {
    if (children != null) return;
    List<Configurable> configurables = DebuggerConfigurable.getConfigurables(getCategory());
    children = configurables.toArray(new Configurable[0]);
  }


  @Nullable
  @Override
  public final JComponent createComponent() {
    if (rootComponent == null) {
      if (root == null) {
        root = createRootUi();
      }

      buildConfigurables();
      if (isChildrenMerged()) {
        if (root == null) {
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
      DebuggerConfigurableProvider.EXTENSION_POINT.getExtensionList().forEach(provider -> provider.generalApplied(getCategory()));
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
