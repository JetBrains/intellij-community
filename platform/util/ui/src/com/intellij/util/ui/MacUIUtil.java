// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;

public final class MacUIUtil {
  private static final Logger LOG = Logger.getInstance(MacUIUtil.class);
  public static final boolean USE_QUARTZ = "true".equals(System.getProperty("apple.awt.graphics.UseQuartz"));
  public static final String MAC_FILL_BORDER = "MAC_FILL_BORDER";
  private static Cursor INVERTED_TEXT_CURSOR;

  private MacUIUtil() {}

  public static void hideCursor() {
    if (SystemInfoRt.isMac && Registry.is("ide.mac.hide.cursor.when.typing", true)) {
      Foundation.executeOnMainThread(false, false, () -> {
        Foundation.invoke("NSCursor", "setHiddenUntilMouseMoves:", true);
      });
    }
  }

  /**
   * Sets a native predefined cursor on macOS
   * <p>
   *   The intended use is to work around a macOS bug when a cursor set the normal way doesn't appear,
   *   the call is simply ignored.
   *   This often happens when the mouse enters a window with rounded corners through a corner.
   *   Abusing this workaround can cause high CPU usage (IDEA-167733).
   * </p>
   * @deprecated Do not use unless absolutely necessary, normally setting a cursor for a component should be enough.
   *
   * @param type
   */
  @ApiStatus.Internal
  @Deprecated
  public static void nativeSetBuiltInCursor(int type) {
    try {
      var cursorManagerClass = Class.forName("sun.lwawt.macosx.CCursorManager");
      var cursorManager = ReflectionUtil.getField(cursorManagerClass, null, cursorManagerClass, "theInstance");
      var method = ReflectionUtil.getDeclaredMethod(cursorManagerClass, "nativeSetBuiltInCursor", int.class, String.class);
      Objects.requireNonNull(method).invoke(cursorManager, type, null);
    }
    catch (Exception e) {
      LOG.error("Couldn't invoke sun.lwawt.macosx.CCursorManager.nativeSetBuiltInCursor", e);
    }
  }

  /**
   * @deprecated The inverted text cursor is not needed for macOS 10.14 Mojave and later, because the default I-beam cursor
   * now has a thick white border and is visible on a dark background.
   * The inverted cursor also doesn't adjust to color customization in the System Settings, so its usage should be avoided.
   */
  @Deprecated
  public static @NotNull Cursor getInvertedTextCursor() {
    if (INVERTED_TEXT_CURSOR == null) {
      Toolkit toolkit = Toolkit.getDefaultToolkit();
      // will also load text@2x.png
      Image cursorImage = toolkit.getImage(MacUIUtil.class.getClassLoader().getResource("mac/text.png"));
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
