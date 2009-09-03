package com.intellij.openapi.ui;

import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

import com.intellij.util.concurrency.Semaphore;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class SplitterTest extends TestCase{
  private static final int RATHER_LATER_INVOKES = 10;

  public void testResizeVert() {
    resizeTest(new Splitter(true));
  }

  public void testResizeHoriz() {
    resizeTest(new Splitter(false));
  }

  private void resizeTest(Splitter splitter) {
    JPanel jPanel1 = new JPanel();
    jPanel1.setMinimumSize(new Dimension(100, 100));
    JPanel jPanel2 = new JPanel();
    jPanel2.setMinimumSize(new Dimension(100, 100));
    splitter.setFirstComponent(jPanel1);
    splitter.setSecondComponent(jPanel2);
    splitter.setHonorComponentsMinimumSize(true);


    splitter.setSize(new Dimension(500, 500));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(300, 300));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setProportion(.1f);
    splitter.doLayout();
    checkBounds(splitter);

    //assertTrue(Math.abs(splitter.getProportion() - jPanel1.getMinimumSize().height / (splitter.getSize().height - splitter.getDividerWidth())) < .00001);

    splitter.setProportion(.9f);
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(100, 100));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setProportion(.1f);
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(10, 10));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(100, 100));
    splitter.doLayout();
    checkBounds(splitter);

    splitter.setSize(new Dimension(150, 150));
    splitter.doLayout();
    checkBounds(splitter);
  }


  private void checkBounds(Splitter splitter) {
    Dimension firstSize = splitter.getFirstComponent().getSize();
    Dimension secondSize = splitter.getSecondComponent().getSize();

    Dimension size = splitter.getSize();

    if(splitter.getOrientation()) { // Split horizontally
      assertTrue(firstSize.height + splitter.getDividerWidth() + secondSize.height == size.height);
      assertTrue(firstSize.width == size.width && secondSize.width == size.width);
    }
    else {
      assertTrue(firstSize.width + splitter.getDividerWidth() + secondSize.width == size.width);
      assertTrue(firstSize.height == size.height && secondSize.height == size.height);
    }

    if(splitter.isHonorMinimumSize()) {
      Dimension firstMinimum = splitter.getFirstComponent().getMinimumSize();
      Dimension secondMinimum = splitter.getSecondComponent().getMinimumSize();

      assertTrue(firstSize.width < firstMinimum.width == secondSize.width < secondMinimum.width);
      assertTrue(firstSize.height < firstMinimum.height == secondSize.height < secondMinimum.height);
    }
  }

  private void invokeRatherLater(final Runnable runnable) {
    invokeRatherLater(runnable, RATHER_LATER_INVOKES);
  }

  private void invokeRatherLater(final Runnable runnable, final int n) {
    if(n == 0) {
      runnable.run();
    }
    else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          invokeRatherLater(runnable, n - 1);
        }
      });
    }
  }


}
