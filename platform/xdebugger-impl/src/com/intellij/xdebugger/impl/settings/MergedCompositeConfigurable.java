package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TitledSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

abstract class MergedCompositeConfigurable implements SearchableConfigurable {
  static final EmptyBorder BOTTOM_INSETS = new EmptyBorder(0, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);

  protected final Configurable[] children;
  protected JComponent rootComponent;

  protected MergedCompositeConfigurable(@NotNull Configurable[] children) {
    this.children = children;
  }

  protected boolean isUseTitledBorder() {
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
        JPanel panel = createPanel(isUseTitledBorder());
        for (Configurable child : children) {
          JComponent component = child.createComponent();
          assert component != null;
          if (isUseTitledBorder()) {
            String displayName = child.getDisplayName();
            if (StringUtil.isEmpty(displayName)) {
              component.setBorder(BOTTOM_INSETS);
            }
            else {
              Insets insets = new Insets(children[0] == child ? 0 : IdeBorderFactory.TITLED_BORDER_TOP_INSET, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);
              component.setBorder(IdeBorderFactory.createTitledBorder(displayName, false, insets));
            }
          }
          panel.add(component);
        }
        rootComponent = panel;
      }
    }
    return rootComponent;
  }

  @NotNull
  static JPanel createPanel(boolean isUseTitledBorder) {
    int verticalGap = TitledSeparator.TOP_INSET;
    JPanel panel = new JPanel(new VerticalFlowLayout(0, isUseTitledBorder ? 0 : verticalGap));
    // VerticalFlowLayout incorrectly use vertical gap as top inset
    if (!isUseTitledBorder) {
      panel.setBorder(new EmptyBorder(-verticalGap, 0, 0, 0));
    }
    return panel;
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