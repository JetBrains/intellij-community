// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.function.Function;

@ApiStatus.Internal
public final class OwnerOptional {
  public static @Nullable Window findOwner(@Nullable Component parent) {
    if (parent == null) {
      parent = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (parent == null) {
        parent = Window.getWindows()[0];
      }
    }

    var owner = parent instanceof Window ? (Window)parent : SwingUtilities.getWindowAncestor(parent);

    if (IdeEventQueue.getInstance().getPopupManager().isPopupWindow(owner)) {
      if (!owner.isFocused() || !SystemInfo.isJetBrainsJvm) {
        do {
          owner = owner.getOwner();
        }
        while (UIUtil.isSimpleWindow(owner));
      }
    }

    if (owner instanceof Dialog ownerDialog && !ownerDialog.isModal() && !UIUtil.isPossibleOwner(ownerDialog)) {
      while (owner instanceof Dialog ownerDialog2 && !ownerDialog2.isModal()) {
        owner = owner.getOwner();
      }
    }

    while (owner != null && !owner.isShowing()) {
      owner = owner.getOwner();
    }

    // `Window` cannot be a parent of `JDialog`
    if (UIUtil.isSimpleWindow(owner)) {
      owner = null;
    }
    return owner;
  }

  public static <T> T create(@Nullable Component parent, Function<Dialog, T> forDialog, Function<@Nullable Frame, T> forFrame) {
    var owner = findOwner(parent);

    if (owner instanceof Dialog dialog) {
      return forDialog.apply(dialog);
    }

    if (owner instanceof IdeFrame.Child childFrame) {
      owner = WindowManager.getInstance().getFrame(childFrame.getProject());
    }

    return forFrame.apply((Frame)owner);
  }
}
