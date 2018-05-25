// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.util.Comparing;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TBItemButton extends TBItem {
  protected @Nullable NSTLibrary.Action myAction;
  protected @Nullable Icon myIcon;
  protected @Nullable String myText;
  protected int myWidth;
  protected int myFlags;

  private int myUpdateOptions;

  protected TBItemButton(@NotNull String uid) { this(uid, null, null, null, -1, 0); }

  TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action) { this(uid, icon, text, action, -1, 0); }

  TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action, int buttWidth) { this(uid, icon, text, action, buttWidth, 0); }

  TBItemButton(@NotNull String uid, Icon icon, String text, NSTLibrary.Action action, int buttWidth, int buttonFlags) {
    super(uid);
    myAction = action;
    myIcon = icon;
    myText = text;
    myWidth = buttWidth;
    myFlags = buttonFlags;
  }

  void setWidth(int width) { _update(myIcon, myText, myAction, width, myFlags); }

  // [-128, 127], 0 is the default value
  void setPriority(byte prio) { _update(myIcon, myText, myAction, myWidth, myFlags | NSTLibrary.priority2mask(prio)); }

  void update(Icon icon, String text, NSTLibrary.Action action) { _update(icon, text, action, myWidth, myFlags); }
  void update(Icon icon, String text) { _update(icon, text, myAction, myWidth, myFlags); }
  void update(Icon icon) { _update(icon, myText, myAction, myWidth, myFlags); }
  void update(Icon icon, String text, boolean isSelected, boolean isDisabled) {
    int flags = _applyFlag(myFlags, isSelected, NSTLibrary.BUTTON_FLAG_SELECTED);
    flags = _applyFlag(flags, isDisabled, NSTLibrary.BUTTON_FLAG_DISABLED);
    _update(icon, text, myAction, myWidth, flags);
  }

  private static boolean _equals(Icon ic0, Icon ic1) {
    if (ic0 == ic1)
      return true;
    return ic0 != null ? ic0.equals(ic1) : ic1.equals(ic0);
  }

  synchronized private void _update(Icon icon, String text, NSTLibrary.Action action, int buttWidth, int buttFlags) {
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
//    System.out.printf("_updateNativePeer, button [%s]: updateOptions 0x%X\n", myUid, myUpdateOptions);
    NST.updateButton(myNativePeer, myUpdateOptions, myWidth, myFlags, text, icon, myAction);
    myUpdateOptions = 0;
  }

  @Override
  synchronized protected ID _createNativePeer() {
//    System.out.printf("_createNativePeer, button [%s]\n", myUid);
    return NST.createButton(myUid, myWidth, myFlags, myText, myIcon, myAction);
  }

  private static int _applyFlag(int src, boolean include, int flag) {
    return include ? (src | flag) : (src & ~flag);
  }
}
