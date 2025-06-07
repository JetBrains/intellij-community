// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
public final class SingleSettingEditor extends ConfigurableEditor {
  private final SimpleBanner myBanner = new SimpleBanner() {
    @Override
    Dimension getPreferredLeftPanelSize(Dimension size) {
      return new Dimension(size.width, JBUIScale.scale(35));
    }
  };
  private final Map<Configurable, ConfigurableController> myControllers = new HashMap<>();
  private ConfigurableController myLastController;
  private Dimension myDialogInitSize;

  SingleSettingEditor(Disposable parent, Configurable configurable) {
    super(parent);
    add(myBanner, BorderLayout.NORTH);
    myBanner.setVisible(false);
    init(configurable, false);

    if (configurable instanceof Configurable.SingleEditorConfiguration singleEditorConfiguration) {
      myDialogInitSize = singleEditorConfiguration.getDialogInitialSize();
    }
  }

  @Override
  void postUpdateCurrent(Configurable configurable) {
    if (myLastController != null) {
      myLastController.setBanner(null);
      myLastController = null;
    }

    ConfigurableController controller = ConfigurableController.getOrCreate(configurable, myControllers);
    if (controller != null) {
      myLastController = controller;
      controller.setBanner(myBanner);
    }

    myBanner.setVisible(myBanner.canShow());
  }

  @Override
  public @Nullable Dimension getDialogInitialSize() {
    return myDialogInitSize;
  }
}