// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

public class MacUIUtil {

  public static final boolean USE_QUARTZ = "true".equals(System.getProperty("apple.awt.graphics.UseQuartz"));
  public static final String MAC_FILL_BORDER = "MAC_FILL_BORDER";
  public static final int MAC_COMBO_BORDER_V_OFFSET = SystemInfo.isMacOSLion ? 1 : 0;
  private static Cursor INVERTED_TEXT_CURSOR;

  private MacUIUtil() {}

  public static void hideCursor() {
    if (SystemInfo.isMac && Registry.is("ide.mac.hide.cursor.when.typing", true)) {
      Foundation.executeOnMainThread(false, false, () -> {
        Foundation.invoke("NSCursor", "setHiddenUntilMouseMoves:", true);
      });
    }
  }

  @NotNull
  public static Cursor getInvertedTextCursor() {
    if (INVERTED_TEXT_CURSOR == null) {
      final Toolkit toolkit = Toolkit.getDefaultToolkit();
      Image cursorImage = toolkit.getImage(MacUIUtil.class.getClassLoader().getResource("/mac/text.png")); // will also load text@2x.png
      INVERTED_TEXT_CURSOR = toolkit.createCustomCursor(cursorImage, new Point(15, 13), "InvertedTextCursor");
    }
    return INVERTED_TEXT_CURSOR;
  }

  /**
   * By default, ctrl+click changes selection in swing trees and tables
   * (see {@link javax.swing.plaf.basic.BasicTreeUI#selectPathForEvent(TreePath, MouseEvent)}
   * or {@link javax.swing.plaf.basic.BasicTableUI.Handler#mousePressed(MouseEvent)}),
   * while it should leave the selection intact and show context menu.
   */
  public static MouseEvent fixMacContextMenuIssue(MouseEvent e) {
    if (SwingUtilities.isLeftMouseButton(e) && e.isControlDown() && e.getID() == MouseEvent.MOUSE_PRESSED) {
      int modifiers = e.getModifiers() & ~(InputEvent.CTRL_MASK | InputEvent.BUTTON1_MASK) | InputEvent.BUTTON3_MASK;
      return new MouseEvent(e.getComponent(), e.getID(), e.getWhen(), modifiers, e.getX(), e.getY(), e.getClickCount(),
                            true, MouseEvent.BUTTON3);
    }
    return e;
  }
}
