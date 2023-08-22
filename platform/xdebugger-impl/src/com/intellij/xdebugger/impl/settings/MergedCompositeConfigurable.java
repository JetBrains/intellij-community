// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.CompositeConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.TitledSeparator;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

class MergedCompositeConfigurable extends CompositeConfigurable<Configurable> implements SearchableConfigurable {
  static final EmptyBorder BOTTOM_INSETS = new EmptyBorder(0, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);

  private static final Insets FIRST_COMPONENT_INSETS = new Insets(0, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);
  private static final Insets N_COMPONENT_INSETS = new Insets(IdeBorderFactory.TITLED_BORDER_TOP_INSET, 0, IdeBorderFactory.TITLED_BORDER_BOTTOM_INSET, 0);

  protected final Configurable[] children;
  protected JComponent rootComponent;

  private final String id;
  private final @Nls String displayName;
  private final String helpTopic;

  MergedCompositeConfigurable(@NotNull String id,
                                     @NotNull @Nls String displayName,
                                     @Nullable String helpTopic,
                                     Configurable @NotNull [] children) {
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
        JComponent component = firstConfigurable.createComponent();
        String rootComponentDisplayName = firstConfigurable.getDisplayName();
        if (!StringUtil.isEmpty(rootComponentDisplayName) && !isTargetedToProduct(firstConfigurable)) {
          component.setBorder(IdeBorderFactory.createTitledBorder(rootComponentDisplayName, false, FIRST_COMPONENT_INSETS));
        }
        rootComponent = createPanel(true);
        rootComponent.add(component);
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
    return ContainerUtil.exists(DebuggerConfigurableProvider.EXTENSION_POINT.getExtensionList(),
                                provider -> provider.isTargetedToProduct(configurable));
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
  public void disposeUIResources() {
    rootComponent = null;
    super.disposeUIResources();
  }

  @NotNull
  @Override
  protected List<Configurable> createConfigurables() {
    return Arrays.asList(children);
  }
}