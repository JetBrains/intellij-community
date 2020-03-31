// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.assertTrue;

public class ScreenUtilTest {
  @Test
  public void testIsMovementTowards() {
    Rectangle bounds = new Rectangle(100, 100);
    assertTrue(ScreenUtil.isMovementTowards(new Point(150, 50), new Point(110, 40), bounds));
    assertTrue(ScreenUtil.isMovementTowards(new Point(150, 50), new Point(110, 60), bounds));
  }
}
