// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.UIEventId;
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DumbServiceBalloon {
  private static final Logger LOG = Logger.getInstance(DumbServiceBalloon.class);

  private static final @NotNull JBInsets DUMB_BALLOON_INSETS = JBInsets.create(5, 8);

  private final Project myProject;
  private final Service myService;
  private Balloon myBalloon;//used from EDT only

  public DumbServiceBalloon(@NotNull Project project,
                            @NotNull Service service) {
    myProject = project;
    myService = service;
  }

  /// a workaround, it should not depend from DumbServiceImpl/DumbService and be a part of DumbServiceImpl
  interface Service {
    boolean isDumb();
    void runWhenSmart(@NotNull Runnable runnable);
  }

  void dispose() {
    if (myBalloon != null) {
      Disposer.dispose(myBalloon);
    }
  }

  void showDumbModeActionBalloon(@NotNull @NlsContexts.PopupContent String balloonText,
                                 @NotNull Runnable runWhenSmartAndBalloonStillShowing) {
    if (LightEdit.owns(myProject)) return;
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myService.isDumb()) {
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonWasNotNeeded, new FeatureUsageData().addProject(myProject));
      runWhenSmartAndBalloonStillShowing.run();
      return;
    }
    if (myBalloon != null) {
      //here should be an assertion that it does not happen, but now we have two dispatches of one InputEvent, see IDEA-227444
      return;
    }
    tryShowBalloonTillSmartMode(balloonText, runWhenSmartAndBalloonStillShowing);
  }

  private void tryShowBalloonTillSmartMode(@NotNull @NlsContexts.PopupContent String balloonText,
                                           @NotNull Runnable runWhenSmartAndBalloonNotHidden) {
    LOG.assertTrue(myBalloon == null);
    long startTimestamp = System.nanoTime();
    UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonRequested, new FeatureUsageData().addProject(myProject));
    myBalloon = JBPopupFactory.getInstance().
      createHtmlTextBalloonBuilder(balloonText, AllIcons.General.BalloonWarning, UIUtil.getToolTipBackground(), null).
      setBorderColor(JBColor.border()).
      setBorderInsets(DUMB_BALLOON_INSETS).
      setShowCallout(false).
      createBalloon();
    myBalloon.setAnimationEnabled(false);
    myBalloon.addListener(new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        if (myBalloon == null) {
          return;
        }
        FeatureUsageData data = new FeatureUsageData().addProject(myProject);
        UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonCancelled, data);
        myBalloon = null;
      }
    });
    myService.runWhenSmart(() -> {
      if (myBalloon == null) {
        return;
      }
      FeatureUsageData data = new FeatureUsageData().addProject(myProject).
        addData("duration_ms", TimeoutUtil.getDurationMillis(startTimestamp));
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonProceededToActions, data);
      runWhenSmartAndBalloonNotHidden.run();
      Balloon balloon = myBalloon;
      myBalloon = null;
      balloon.hide();
    });
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(context -> {
      if (!myService.isDumb()) {
        return;
      }
      if (myBalloon == null) {
        return;
      }
      UIEventLogger.logUIEvent(UIEventId.DumbModeBalloonShown, new FeatureUsageData().addProject(myProject));
      myBalloon.show(getDumbBalloonPopupPoint(myBalloon, context), Balloon.Position.above);
    });
  }

  private static @NotNull RelativePoint getDumbBalloonPopupPoint(@NotNull Balloon balloon, DataContext context) {
    RelativePoint relativePoint = JBPopupFactory.getInstance().guessBestPopupLocation(context);
    Dimension size = balloon.getPreferredSize();
    Point point = relativePoint.getPoint();
    point.translate(size.width / 2, 0);
    //here are included hardcoded insets, icon width and small hardcoded delta to show before guessBestPopupLocation point
    point.translate(-DUMB_BALLOON_INSETS.left - AllIcons.General.BalloonWarning.getIconWidth() - JBUIScale.scale(6), 0);
    return new RelativePoint(relativePoint.getComponent(), point);
  }
}
