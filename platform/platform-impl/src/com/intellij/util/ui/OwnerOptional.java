/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.IdePopupManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Fokin
 */
public class OwnerOptional {

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

      //manager.closeAllPopups();

      if (!owner.isFocused()) {
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
      if (ownerDialog.isModal()) {
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

    return new OwnerOptional(owner);
  }

  public OwnerOptional ifDialog(Consumer<Dialog> consumer) {
    if (myPermanentOwner instanceof Dialog) {
      consumer.consume((Dialog)myPermanentOwner);
    }
    return this;
  }

  public OwnerOptional ifNull(Consumer<Frame> consumer) {
    if (myPermanentOwner == null) {
      consumer.consume(null);
    }
    return this;
  }

  public OwnerOptional ifWindow(Consumer<Window> consumer) {
    if (myPermanentOwner != null) {
      consumer.consume(myPermanentOwner);
    }
    return this;
  }

  public OwnerOptional ifFrame(Consumer<Frame> consumer) {
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
