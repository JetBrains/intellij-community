package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupAdapter;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

/**
* @author Denis Zhdanov
* @since 3/17/12 3:18 PM
*/
class GradleBalloonBuilder implements BalloonBuilder {

  @NotNull private final BalloonBuilder myDelegate;
  @NotNull private final List<Balloon>  myStorage;

  GradleBalloonBuilder(@NotNull BalloonBuilder delegate, @NotNull List<Balloon> storage) {
    myDelegate = delegate;
    myStorage = storage;
  }

  @Override
  @NotNull
  public Balloon createBalloon() {
    final Balloon result = myDelegate.createBalloon();
    myStorage.add(result);
    result.addListener(new JBPopupAdapter() {
      @Override
      public void onClosed(LightweightWindowEvent event) {
        if (!result.isDisposed()) {
          Disposer.dispose(result);
        }
        myStorage.remove(result);
      }
    });
    return result;
  }  
  
  @Override
  @NotNull
  public BalloonBuilder setPreferredPosition(Balloon.Position position) {
    myDelegate.setPreferredPosition(position);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setBorderColor(@NotNull Color color) {
    myDelegate.setBorderColor(color);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setFillColor(@NotNull Color color) {
    myDelegate.setFillColor(color);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setHideOnClickOutside(boolean hide) {
    myDelegate.setHideOnClickOutside(hide);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setHideOnKeyOutside(boolean hide) {
    myDelegate.setHideOnKeyOutside(hide);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setShowCallout(boolean show) {
    myDelegate.setShowCallout(show);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setCloseButtonEnabled(boolean enabled) {
    myDelegate.setCloseButtonEnabled(enabled);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setFadeoutTime(long fadeoutTime) {
    myDelegate.setFadeoutTime(fadeoutTime);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setAnimationCycle(int time) {
    myDelegate.setAnimationCycle(time);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setHideOnFrameResize(boolean hide) {
    myDelegate.setHideOnFrameResize(hide);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setClickHandler(ActionListener listener, boolean closeOnClick) {
    myDelegate.setClickHandler(listener, closeOnClick);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setCalloutShift(int length) {
    myDelegate.setCalloutShift(length);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setPositionChangeXShift(int positionChangeXShift) {
    myDelegate.setPositionChangeXShift(positionChangeXShift);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setPositionChangeYShift(int positionChangeYShift) {
    myDelegate.setPositionChangeYShift(positionChangeYShift);
    return this;
  }

  @Override
  public boolean isHideOnAction() {
    return myDelegate.isHideOnAction();
  }

  @Override
  @NotNull
  public BalloonBuilder setHideOnAction(boolean hideOnAction) {
    myDelegate.setHideOnAction(hideOnAction);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setDialogMode(boolean dialogMode) {
    myDelegate.setDialogMode(dialogMode);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setTitle(@Nullable String title) {
    myDelegate.setTitle(title);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setContentInsets(Insets insets) {
    myDelegate.setContentInsets(insets);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setShadow(boolean shadow) {
    myDelegate.setShadow(shadow);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setSmallVariant(boolean smallVariant) {
    myDelegate.setSmallVariant(smallVariant);
    return this;
  }

  @Override
  @NotNull
  public BalloonBuilder setLayer(Balloon.Layer layer) {
    myDelegate.setLayer(layer);
    return this;
  }
}
