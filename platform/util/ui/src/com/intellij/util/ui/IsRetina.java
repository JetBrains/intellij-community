// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import apple.awt.CImage;

import java.awt.image.BufferedImage;

final class IsRetina {
  public static boolean isRetina() {
    try {
      final boolean[] isRetina = new boolean[1];
      new CImage.HiDPIScaledImage(1, 1, BufferedImage.TYPE_INT_ARGB) {
        @Override
        public void drawIntoImage(BufferedImage image, float v) {
          isRetina[0] = v > 1;
        }
      };
      return isRetina[0];
    }
    catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }
}
