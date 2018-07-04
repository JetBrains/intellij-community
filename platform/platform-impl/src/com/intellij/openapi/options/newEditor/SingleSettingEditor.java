// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.options.Configurable;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class SingleSettingEditor extends ConfigurableEditor {
  private final SimpleBanner myBanner = new SimpleBanner();
  private final Map<Configurable, ConfigurableController> myControllers = new HashMap<>();
  private ConfigurableController myLastController;

  SingleSettingEditor(Disposable parent, Configurable configurable) {
    super(parent);
    add(myBanner, BorderLayout.NORTH);
    myBanner.setVisible(false);
    init(configurable, false);
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
}