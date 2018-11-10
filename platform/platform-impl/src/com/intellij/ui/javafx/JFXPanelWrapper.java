// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.util.FieldAccessor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.sun.javafx.embed.EmbeddedSceneInterface;
import com.sun.javafx.tk.TKScene;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

import java.awt.*;

public class JFXPanelWrapper extends JFXPanel {
  private static final FieldAccessor<JFXPanel, Integer> myScaleFactorAccessor = new FieldAccessor<>(JFXPanel.class, "scaleFactor");

  public JFXPanelWrapper() {
    Platform.setImplicitExit(false);
  }

  /**
   * This override fixes the situation of using multiple JFXPanels
   * with jbtabs/splitters when some of them are not showing.
   * On getMinimumSize there is no layout manager nor peer so
   * the result could be #size() which is incorrect.
   * @return zero size
   */
  @Override
  public Dimension getMinimumSize() {
    return new Dimension(0, 0);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (UIUtil.isJreHiDPIEnabled()) {
      // JFXPanel is scaled asynchronously after first repaint, what may lead
      // to showing unscaled content. To work it around, set "scaleFactor" ahead.
      int scale = Math.round(JBUI.sysScale(this));
      myScaleFactorAccessor.set(this, scale);
      Scene scene = getScene();
      // If scene is null then it will be set later and super.setEmbeddedScene(..) will init its scale properly,
      // otherwise explicitly set scene scale to match JFXPanel.scaleFactor.
      if (scene != null) {
        TKScene tks = scene.impl_getPeer();
        if (tks instanceof EmbeddedSceneInterface) {
          ((EmbeddedSceneInterface)tks).setPixelScaleFactor(scale);
        }
      }
    }
  }
}
