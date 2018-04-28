// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class TouchbarTest {
  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      Foundation.init();

      final ID pool = Foundation.invoke("NSAutoreleasePool", "new");
      try {
        final TouchBar testTB = new TouchBar("test", false);
        testTB.addSpacing(true);
        testTB.addButton(null, "test1", createPrintTextCallback("pressed test1 button"));
        testTB.addButton(null, "test2", createPrintTextCallback("pressed test2 button"));
        testTB.addSpacing(false);
        testTB.addButton(AllIcons.Toolwindows.ToolWindowRun, null, createPrintTextCallback("pressed image button"));

        final TouchBar tapHoldTB = new TouchBar("test_popover_tap_and_hold", false);
        final TouchBar expandTB = new TouchBar("test_configs_popover_expand", false);
        final int configPopoverWidth = 143;
        testTB.addPopover(AllIcons.Toolwindows.ToolWindowBuild, "test-popover", configPopoverWidth, expandTB, tapHoldTB);

        expandTB.addButton(AllIcons.Toolwindows.ToolWindowDebugger, null, createPrintTextCallback("pressed pimage button"));
        final TBItemScrubber scrubber = expandTB.addScrubber(450);
        List<TBItemScrubber.ItemData> scrubberItems = new ArrayList<>();
        for (int c = 0; c < 15; ++c) {
          String txt;
          if (c == 7)           txt = "very very long configuration name (debugging type)";
          else                  txt = String.format("r%1.2f", Math.random());
          int finalC = c;
          scrubberItems.add(new TBItemScrubber.ItemData(AllIcons.Toolwindows.ToolWindowPalette, txt,
                            () -> System.out.println("JAVA: performed action of scrubber item at index " + finalC + " [thread:" + Thread.currentThread() + "]")));
        }
        expandTB.selectVisibleItemsToShow();

        tapHoldTB.addButton(AllIcons.Toolwindows.ToolWindowPalette, null, createPrintTextCallback("pressed pimage button"));
        tapHoldTB.selectVisibleItemsToShow();

        NST.setTouchBar(testTB);
      } finally {
        Foundation.invoke(pool, "release");
      }

      final JFrame f = new JFrame();
      f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      f.setBounds(0, 0, 500, 110);
      f.setVisible(true);
    });
  }

  private static NSTLibrary.Action createPrintTextCallback(String text) {
    return new NSTLibrary.Action() {
      @Override
      public void execute() {
        System.out.println(text + " [thread:" + Thread.currentThread() + "]");
      }
    };
  }
}

