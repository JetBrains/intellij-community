// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ui.mac.foundation.Foundation;

import javax.swing.*;

public class TouchbarTest {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      Foundation.init();
      TouchBarManager.initialize();
      TouchBarManager.setCurrent(TouchBarManager.TOUCHBARS.test);

      final JFrame f = new JFrame();
      f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      f.setBounds(0, 0, 500, 110);
      f.setVisible(true);
    });
  }
}

