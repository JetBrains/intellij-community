// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class MergedCompositeConfigurable implements SearchableConfigurable {
  static final EmptyBorder BOTTOM_INSETS = new EmptyBorder(0, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);

  private static final Insets FIRST_COMPONENT_INSETS = new Insets(0, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);
  private static final Insets N_COMPONENT_INSETS = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);

  protected final Configurable[] children;
  protected JComponent rootComponent;

  private final String id;
  private final String displayName;
  private final String helpTopic;

  public MergedCompositeConfigurable(@NotNull String id,
                                     @NotNull String displayName,
                                     @Nullable String helpTopic,
                                     @NotNull Configurable[] children) {
    this.children = children;
    this.id = id;
    this.displayName = displayName;
    this.helpTopic = helpTopic;
  }

  @NotNull
  @Override
  public String getId() {
    return id;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    if (helpTopic != null) {
      return helpTopic;
    }
    return children.length == 1 ? children[0].getHelpTopic() : null;
  }

  /**
   * false by default.
   *
   * If Ruby general settings will be without titled border in RubyMine, user could think that all other debugger categories also about Ruby.
   */
  protected boolean isUseTargetedProductPolicyIfSeveralChildren() {
    return false;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (rootComponent == null) {
      Configurable firstConfigurable = children[0];
      if (children.length == 1) {
        rootComponent = firstConfigurable.createComponent();
        String rootComponentDisplayName = firstConfigurable.getDisplayName();
        if (!StringUtil.isEmpty(rootComponentDisplayName) && !isTargetedToProduct(firstConfigurable)) {
          rootComponent.setBorder(IdeBorderFactory.createTitledBorder(rootComponentDisplayName, false, FIRST_COMPONENT_INSETS));
        }
      }
      else {
        boolean isFirstNamed = true;
        JPanel panel = createPanel(true);
        for (Configurable configurable : children) {
          JComponent component = configurable.createComponent();
          assert component != null;
          String displayName = configurable.getDisplayName();
          if (StringUtil.isEmpty(displayName)) {
            component.setBorder(BOTTOM_INSETS);
          }
          else {
            boolean addBorder = true;
            if (isUseTargetedProductPolicyIfSeveralChildren() && isFirstNamed) {
              isFirstNamed = false;
              if (isTargetedToProduct(configurable)) {
                addBorder = false;
              }
            }
            if (addBorder) {
              component.setBorder(IdeBorderFactory.createTitledBorder(displayName, false, firstConfigurable == configurable ? FIRST_COMPONENT_INSETS : N_COMPONENT_INSETS));
            }
          }
          panel.add(component);
        }
        rootComponent = panel;
      }
    }
    return rootComponent;
  }

  static boolean isTargetedToProduct(@NotNull Configurable configurable) {
    for (DebuggerConfigurableProvider provider : DebuggerConfigurableProvider.EXTENSION_POINT.getExtensions()) {
      if (provider.isTargetedToProduct(configurable)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  static JPanel createPanel(boolean isUseTitledBorder) {
    int verticalGap = TitledSeparator.TOP_INSET;
    JPanel panel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, isUseTitledBorder ? 0 : verticalGap, true, true));
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