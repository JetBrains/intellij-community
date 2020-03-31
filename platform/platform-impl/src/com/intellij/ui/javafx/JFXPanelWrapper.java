// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.javafx;

import com.intellij.ui.JreHiDpiUtil;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.FieldAccessor;
import com.sun.javafx.embed.EmbeddedSceneInterface;
import com.sun.javafx.tk.TKScene;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;

import java.awt.*;

public class JFXPanelWrapper extends JFXPanel {
  private static final FieldAccessor<JFXPanel, Integer> myScaleFactorAccessor = new FieldAccessor<>(JFXPanel.class, "scaleFactor", Integer.TYPE);

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
    // todo: remove it when IDEA finally switches to JFX10
    if (myScaleFactorAccessor.isAvailable()) {
      if (JreHiDpiUtil.isJreHiDPIEnabled()) {
        // JFXPanel is scaled asynchronously after first repaint, what may lead
        // to showing unscaled content. To work it around, set "scaleFactor" ahead.
        int scale = Math.round(JBUIScale.sysScale(this));
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
    // change scale factor before component will be resized in super
    super.addNotify();
  }
}
