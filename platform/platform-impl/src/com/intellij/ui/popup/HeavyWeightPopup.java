// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

// This class is intended to be a wrapper over JDK's heavyweight popup, which disables popup recycling
public class HeavyWeightPopup extends Popup {
  private final Popup myDelegate;
  private final Window myPopupWindow;

  public HeavyWeightPopup(@NotNull Popup delegate, @NotNull Window popupWindow) {
    myDelegate = delegate;
    myPopupWindow = popupWindow;
  }

  @Override
  public void show() {
    myDelegate.show();
  }

  @Override
  public void hide() {
    myPopupWindow.dispose();
  }

  public @NotNull Window getWindow() {
    return myPopupWindow;
  }
}
