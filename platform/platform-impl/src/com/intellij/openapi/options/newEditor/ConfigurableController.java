// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
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
  private boolean myHasProject;

  void setBanner(SimpleBanner banner) {
    if (banner == null) {
      myBanner.setLeftComponent(null);
      myBanner.setCenterComponent(null);
      myBanner.showProgress(false);
      showProject(myBanner, true);
    }
    else {
      banner.setLeftComponent(myLeftComponent);
      banner.setCenterComponent(myCenterComponent);
      banner.showProgress(myProgress);
      showProject(banner, myHasProject);

      IJSwingUtilities.updateComponentTreeUI(banner);
    }

    myBanner = banner;
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

  @Override
  public void showProject(boolean hasProject) {
    myHasProject = hasProject;
    showProject(myBanner, hasProject);
  }

  static @Nullable ConfigurableController getOrCreate(@Nullable Configurable configurable,
                                                      @NotNull Map<Configurable, ConfigurableController> controllers) {
    ConfigurableController controller = controllers.get(configurable);

    if (controller == null) {
      UnnamedConfigurable original = configurable instanceof ConfigurableWrapper ?
                                     ((ConfigurableWrapper)configurable).getConfigurable() :
                                     configurable;

      controller = original instanceof Configurable.TopComponentProvider ?
                   createController((Configurable.TopComponentProvider)original) :
                   null;

      if (controller != null) {
        controllers.put(configurable, controller);
      }
    }

    return controller;
  }

  private static @Nullable ConfigurableController createController(@NotNull Configurable.TopComponentProvider original) {
    if (!original.isAvailable()) {
      return null;
    }

    ConfigurableController controller = new ConfigurableController();
    controller.myCenterComponent = original.getCenterComponent(controller);
    return controller;
  }

  private static void showProject(@Nullable SimpleBanner banner,
                                  boolean hasProject) {
    if (banner instanceof Banner) {
      ((Banner)banner).showProject(hasProject);
    }
  }
}