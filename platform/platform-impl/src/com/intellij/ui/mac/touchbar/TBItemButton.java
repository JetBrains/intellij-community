// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

class TBItemButton extends TBItem {
  protected @Nullable Icon myIcon;
  protected @Nullable String myText;
  protected int myWidth = -1;
  protected int myFlags = 0;

  private @Nullable Runnable myAction;
  private @Nullable NSTLibrary.Action myNativeCallback;

  private int myUpdateOptions;

  TBItemButton(@NotNull String uid, @Nullable ItemListener listener) { super(uid, listener); }

  TBItemButton setIcon(Icon icon) {
    if (icon != null) icon = IconLoader.getDarkIcon(icon, true);

    if (!_equals(icon, myIcon)) {
      myIcon = icon;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
        _updateNativePeer();
      }
    }

    return this;
  }

  TBItemButton setText(String text) {
    if (!Comparing.equal(text, myText)) {
      myText = text;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
        _updateNativePeer();
      }
    }

    return this;
  }

  TBItemButton setActionOnEDT(Runnable action) { return setAction(action, true, null);}

  TBItemButton setThreadSafeAction(Runnable action) { return setAction(action, false, null);}

  TBItemButton setAction(Runnable action, boolean executeOnEDT, ModalityState modality) {
    if (action != myAction) {
      myAction = action;
      if (myAction == null)
        myNativeCallback = null;
      else
        myNativeCallback = ()->{
          // NOTE: executed from AppKit thread
          if (executeOnEDT) {
            if (modality != null)
              ApplicationManager.getApplication().invokeLater(myAction, modality);
            else
              ApplicationManager.getApplication().invokeLater(myAction);
          } else {
            myAction.run();
          }

          if (myListener != null)
            myListener.onItemEvent(this, 0);
        };

      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_ACTION;
        _updateNativePeer();
      }
    }

    return this;
  }

  TBItemButton setWidth(int width) {
    if (width != myWidth) {
      myWidth = width;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_WIDTH;
        _updateNativePeer();
      }
    }

    return this;
  }

  // [-128, 127], 0 is the default value
  TBItemButton setPriority(byte prio) {
    final int flags = myFlags | NSTLibrary.priority2mask(prio);
    if (flags != myFlags) {
      myFlags = flags;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
        _updateNativePeer();
      }
    }

    return this;
  }

  TBItemButton setToggle(boolean toggle) {
    int flags = _applyFlag(myFlags, toggle, NSTLibrary.BUTTON_FLAG_TOGGLE);
    if (flags != myFlags) {
      myFlags = flags;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
        _updateNativePeer();
      }
    }

    return this;
  }

  TBItemButton setColored(boolean isColored) {
    final int flags = _applyFlag(myFlags, isColored, NSTLibrary.BUTTON_FLAG_COLORED);
    if (flags != myFlags) {
      myFlags = flags;
      if (myNativePeer != ID.NIL) {
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
        _updateNativePeer();
      }
    }

    return this;
  }

  void update(Icon icon, String text, boolean isSelected, boolean isDisabled) {
    if (icon != null) icon = IconLoader.getDarkIcon(icon, true);

    int flags = _applyFlag(myFlags, isSelected, NSTLibrary.BUTTON_FLAG_SELECTED);
    flags = _applyFlag(flags, isDisabled, NSTLibrary.BUTTON_FLAG_DISABLED);
    _update(icon, text, myAction, myWidth, flags);
  }

  private static boolean _equals(Icon ic0, Icon ic1) {
    if (ic0 == ic1)
      return true;
    return ic0 != null ? ic0.equals(ic1) : ic1.equals(ic0);
  }

  synchronized private void _update(Icon icon, String text, Runnable action, int buttWidth, int buttFlags) {
    if (myNativePeer != ID.NIL) {
      if (!_equals(icon, myIcon)) {
        // NOTE: some of layered buttons (like 'stop' or 'debug') can change the icon-object permanently (every second) without any visible differences
        // System.out.printf("\tbutton [%s]: icon has been changed %s -> %s\n", myUid, _icon2string(myIcon), _icon2string(icon));
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
      }
      if (!Comparing.equal(text, myText))
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
      if (action != myAction)
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_ACTION;
      if (buttWidth != myWidth)
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_WIDTH;
      if (buttFlags != myFlags)
        myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }
    myIcon = icon;
    myText = text;
    myAction = action;
    myWidth = buttWidth;
    myFlags = buttFlags;
    if (myUpdateOptions != 0)
      updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() {
    final Icon icon = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_IMG) != 0 ? myIcon : null;
    final String text = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myText : null;
    final NSTLibrary.Action callback = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_ACTION) != 0 ? myNativeCallback : null;
//    System.out.printf("_updateNativePeer, button [%s]: updateOptions 0x%X\n", myUid, myUpdateOptions);
    final int validFlags = _validateFlags();
    NST.updateButton(myNativePeer, myUpdateOptions, myWidth, validFlags, text, icon, callback);
    myUpdateOptions = 0;
  }

  @Override
  synchronized protected ID _createNativePeer() {
//    System.out.printf("_createNativePeer, button [%s]\n", myUid);
    return NST.createButton(myUid, myWidth, _validateFlags(), myText, myIcon, myNativeCallback);
  }

  private int _validateFlags() {
    if ((myFlags & NSTLibrary.BUTTON_FLAG_COLORED) != 0 && (myFlags & NSTLibrary.BUTTON_FLAG_DISABLED) != 0)
      return myFlags & ~NSTLibrary.BUTTON_FLAG_COLORED;
    return myFlags;
  }

  private static int _applyFlag(int src, boolean include, int flag) {
    return include ? (src | flag) : (src & ~flag);
  }
}
