// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
class ConfigurableController implements Configurable.TopComponentController {
  private SimpleBanner myBanner;
  private Component myCenterComponent;
  private Component myLeftComponent;
  private boolean myProgress;

  void setBanner(SimpleBanner banner) {
    if (banner == null) {
      myBanner.setLeftComponent(null);
      myBanner.setCenterComponent(null);
      myBanner.showProgress(false);
    }
    else {
      banner.setLeftComponent(myLeftComponent);
      banner.setCenterComponent(myCenterComponent);
      banner.showProgress(myProgress);
    }

    myBanner = banner;
  }

  void setCenterComponent(Component centerComponent) {
    myCenterComponent = centerComponent;
  }

  @Override
  public void setLeftComponent(@Nullable Component component) {
    myLeftComponent = component;
    if (myBanner != null) {
      myBanner.setLeftComponent(component);
    }
  }

  @Override
  public void showProgress(boolean start) {
    myProgress = start;
    if (myBanner != null) {
      myBanner.showProgress(start);
    }
  }

  static ConfigurableController getOrCreate(Configurable configurable, Map<Configurable, ConfigurableController> controllers) {
    ConfigurableController controller = controllers.get(configurable);
    if (controller != null) {
      return controller;
    }

    Object original = configurable;
    if (configurable instanceof ConfigurableWrapper) {
      original = ((ConfigurableWrapper)configurable).getConfigurable();
    }

    if (original instanceof Configurable.TopComponentProvider && ((Configurable.TopComponentProvider)original).isAvailable()) {
      controller = new ConfigurableController();
      controller.setCenterComponent(((Configurable.TopComponentProvider)original).getCenterComponent(controller));
      controllers.put(configurable, controller);
      return controller;
    }

    return null;
  }
}