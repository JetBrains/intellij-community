// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdePopupManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Fokin
 */
public final class OwnerOptional {
  private static Window findOwnerByComponent(Component component) {
    if (component == null) component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    if (component == null) {
      component = Window.getWindows()[0];
    }
    return (component instanceof Window) ? (Window) component : SwingUtilities.getWindowAncestor(component);
  }

  private Window myPermanentOwner;

  private OwnerOptional(Window permanentOwner) {
    this.myPermanentOwner = permanentOwner;
  }

  public static OwnerOptional fromComponent (Component parentComponent) {
    Window owner = findOwnerByComponent(parentComponent);

    IdePopupManager manager = IdeEventQueue.getInstance().getPopupManager();

    if (manager.isPopupWindow(owner)) {
      if (!owner.isFocused() || !SystemInfo.isJetBrainsJvm) {
        owner = owner.getOwner();

        while (owner != null
               && !(owner instanceof Dialog)
               && !(owner instanceof Frame)) {
          owner = owner.getOwner();
        }
      }
    }

    if (owner instanceof Dialog) {
      Dialog ownerDialog = (Dialog)owner;
      if (ownerDialog.isModal() || UIUtil.isPossibleOwner(ownerDialog)) {
        owner = ownerDialog;
      }
      else {
        while (owner instanceof Dialog && !((Dialog)owner).isModal()) {
          owner = owner.getOwner();
        }
      }
    }

    while (owner != null && !owner.isShowing()) {
      owner = owner.getOwner();
    }

    // Window cannot be parent of JDialog ()
    if (owner != null && !(owner instanceof Frame || owner instanceof Dialog)) {
      owner = null;
    }

    return new OwnerOptional(owner);
  }

  public OwnerOptional ifDialog(Consumer<? super Dialog> consumer) {
    if (myPermanentOwner instanceof Dialog) {
      consumer.consume((Dialog)myPermanentOwner);
    }
    return this;
  }

  public OwnerOptional ifNull(Consumer<? super Frame> consumer) {
    if (myPermanentOwner == null) {
      consumer.consume(null);
    }
    return this;
  }

  public OwnerOptional ifWindow(Consumer<? super Window> consumer) {
    if (myPermanentOwner != null) {
      consumer.consume(myPermanentOwner);
    }
    return this;
  }

  public OwnerOptional ifFrame(Consumer<? super Frame> consumer) {
    if (myPermanentOwner instanceof Frame) {
      if (myPermanentOwner instanceof IdeFrame.Child) {
        IdeFrame.Child ideFrameChild = (IdeFrame.Child)myPermanentOwner;
        myPermanentOwner = WindowManager.getInstance().getFrame(ideFrameChild.getProject());
      }
      consumer.consume((Frame)this.myPermanentOwner);
    }
    return this;
  }

  public Window get() {
    return myPermanentOwner;
  }
}
