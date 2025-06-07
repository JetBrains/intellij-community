// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.ID;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Random;

public final class TouchbarTest {
  private static final Icon ourTestIcon = IconLoader.getIcon("modules/edit.png", TouchbarTest.class.getClassLoader());

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> _createFrame());
  }

  private static void _createFrame() {
    NST.loadLibraryImpl();

    final JFrame f = new JFrame();
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    f.setBounds(0, 0, 500, 110);
    f.setVisible(true);

    new Thread(()-> {
      int c = 1;
      while (--c >= 0) {
        final TBPanel testTB = _createSimpleTestTouchbar();
        testTB.selectVisibleItemsToShow();
        testTB.setTo(null);

        try {
          Thread.sleep(2000);
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }

        NST.setTouchBar(null, ID.NIL);
        testTB.release();
      }
    }, "com.intellij.ui.mac.touchbar.TouchbarTest._createFrame").start();

  }

  private static TBPanel _createSimpleTestTouchbar() {
    final int configPopoverWidth = 143;
    final TBPanel testTB = new TBPanel("test_simple");
    testTB.addButton().setText("butt").setAction(createPrintTextCallback("pressed button"), false);
    return testTB;
  }

  private static TBPanel _createTestButtonsTouchbar() {
    final int configPopoverWidth = 143;
    final TBPanel testTB = new TBPanel("test");
    testTB.addButton().setText("test1").setAction(createPrintTextCallback("pressed test1 button"), false);
    testTB.addButton().setText("test2").setAction(createPrintTextCallback("pressed test2 button"), false);
    testTB.addButton().setText("test3 with suff").setAction(createPrintTextCallback("pressed test2 button"), false);
    testTB.addButton().setIcon(ourTestIcon).setAction(createPrintTextCallback("pressed image button"), false);
    testTB.addButton().setIcon(ourTestIcon).setText("IDEA very-very-very-very long suffix").setWidth(configPopoverWidth).setAction(createPrintTextCallback("pressed image-text button"), false);
    testTB.addButton().setIcon(ourTestIcon).setText("IDEA very long suffix").setWidth(configPopoverWidth + 69).setAction(createPrintTextCallback("pressed image-text 2 button"), false).setToggle();
    return testTB;
  }

  private static Collection<Integer> ourIndices;

  private static Collection<Integer> _makeRandomCollection(int maxIndex) {
    if (ourIndices != null)
      return ourIndices;

    final Random rnd = new Random(System.currentTimeMillis());
    final int size = rnd.nextInt(maxIndex/2);
    ourIndices = new HashSet<>();

    for (int c = 0; c < size; ++c) {
      final int id = rnd.nextInt(maxIndex);
      ourIndices.add(id);
      // System.out.println("\t" + id);
    }

    ourIndices.remove(1);
    ourIndices.remove(2);
    return ourIndices;
  }

  private static boolean ourVisible = true;
  private static boolean ourEnabled = true;
  private static TBPanel _createTestScrubberTouchbar() {
    final TBPanel testTB = new TBPanel("test");
    testTB.addSpacing(true);

    final TBItemScrubber scrubber = testTB.addScrubber();
    final int size = 130;
    for (int c = 0; c < size; ++c) {
      String txt = String.format("%d[%1.2f]", c, Math.random());
      int finalC = c;
      Runnable action = () -> System.out.println("performed action of scrubber item at index " + finalC + " [thread:" + Thread.currentThread() + "]");
      if (c == 11) {
        txt = "very very long text";
      } else if (c == 1) {
        txt = "show";
        action = ()->SwingUtilities.invokeLater(()->{
          ourVisible = !ourVisible;
          NST.showScrubberItem(scrubber.getNativePeer(), _makeRandomCollection(size - 1), ourVisible, false);
        });
      } else if (c == 2) {
        txt = "enable";
        action = ()->SwingUtilities.invokeLater(()->{
          ourEnabled = !ourEnabled;
          NST.enableScrubberItems(scrubber.getNativePeer(), _makeRandomCollection(size - 1), ourEnabled);
        });
      }

      scrubber.addItem(ourTestIcon, txt, action);
    }

    return testTB;
  }

  private static TBPanel _createTestAllTouchbar() {
    final TBPanel testTB = new TBPanel("test");
    testTB.addSpacing(true);
    testTB.addButton().setText("test1").setAction(createPrintTextCallback("pressed test1 button"), false);
    testTB.addButton().setText("test2").setAction(createPrintTextCallback("pressed test2 button"), false);
    testTB.addSpacing(false);
    testTB.addButton().setIcon(ourTestIcon).setAction(createPrintTextCallback("pressed image button"), false);

    return testTB;
  }

  private static Runnable createPrintTextCallback(String text) {
    return ()-> System.out.println(text + " [thread:" + Thread.currentThread() + "]");
  }
}

