package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class MergedCompositeConfigurable implements SearchableConfigurable {
  protected final Configurable[] children;
  protected JComponent rootComponent;

  protected MergedCompositeConfigurable(@NotNull Configurable[] children) {
    this.children = children;
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return children.length == 1 ? children[0].getHelpTopic() : null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (rootComponent == null) {
      if (children.length == 1) {
        rootComponent = children[0].createComponent();
      }
      else {
        JPanel panel = new JPanel(new VerticalFlowLayout(0, 0));
        for (Configurable child : children) {
          JComponent component = child.createComponent();
          assert component != null;
          component.setBorder(IdeBorderFactory.createTitledBorder(child.getDisplayName(), false));
          panel.add(component);
        }
        rootComponent = panel;
      }
    }
    return rootComponent;
  }

  @Override
  public boolean isModified() {
    for (Configurable child : children) {
      if (child.isModified()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    for (Configurable child : children) {
      if (child.isModified()) {
        child.apply();
      }
    }
  }

  @Override
  public void reset() {
    for (Configurable child : children) {
      child.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    rootComponent = null;

    for (Configurable child : children) {
      child.disposeUIResources();
    }
  }
}