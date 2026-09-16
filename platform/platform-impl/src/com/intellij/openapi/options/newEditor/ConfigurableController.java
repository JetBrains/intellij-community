// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.util.IJSwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
final class ConfigurableController implements Configurable.TopComponentController {
  private SimpleBanner myBanner;
  private Component myCenterComponent;
  private Component myLeftComponent;
  private boolean myProgress;
  private boolean myShowProgressIndicator = true;
  private boolean myHasProject;
  private boolean myShowResetAction = true;
  private int myCenterComponentGap = SimpleBanner.DEFAULT_CENTER_COMPONENT_GAP;

  void setBanner(SimpleBanner banner) {
    if (banner == null) {
      myBanner.setLeftComponent(null);
      myBanner.setCenterComponent(null);
      myBanner.setCenterComponentGap(SimpleBanner.DEFAULT_CENTER_COMPONENT_GAP);
      myBanner.showProgress(false);
      myBanner.setProgressIndicatorVisible(true);
      showProject(myBanner, true);
    }
    else {
      banner.setLeftComponent(myLeftComponent);
      banner.setCenterComponent(myCenterComponent);
      banner.setCenterComponentGap(myCenterComponentGap);
      banner.setProgressIndicatorVisible(myShowProgressIndicator);
      banner.showProgress(myShowProgressIndicator && myProgress);
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
      myBanner.showProgress(myShowProgressIndicator && start);
    }
  }

  @Override
  public void showProgressIndicator(boolean show) {
    myShowProgressIndicator = show;
    if (myBanner != null) {
      myBanner.setProgressIndicatorVisible(show);
      myBanner.showProgress(show && myProgress);
    }
  }

  @Override
  public void showProject(boolean hasProject) {
    myHasProject = hasProject;
    showProject(myBanner, hasProject);
  }

  @Override
  public void showResetAction(boolean show) {
    myShowResetAction = show;
  }

  @Override
  public void setCenterComponentGap(int gap) {
    myCenterComponentGap = gap;
    if (myBanner != null) {
      myBanner.setCenterComponentGap(gap);
    }
  }

  boolean isResetActionVisible() {
    return myShowResetAction;
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
    if (banner instanceof ConfigurableEditorBanner) {
      ((ConfigurableEditorBanner)banner).showProject(hasProject);
    }
  }
}
