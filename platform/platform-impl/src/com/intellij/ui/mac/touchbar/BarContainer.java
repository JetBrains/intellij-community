// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Map;

enum BarType {
  DEFAULT,
  DEBUGGER, // debugger must use context of focused component (for example, to use selected text in the Editor)
  POPUP,
  DIALOG,
  MODAL_DIALOG,
  EDITOR_SEARCH
}

class BarContainer {
  private final @NotNull BarType myType;
  private final Container myParentComponent; // used for focus processing
  private Map<Long, TouchBar> myKeyMask2Alt;
  private @NotNull TouchBar myMain;
  private TouchBar myCurrent;
  private Runnable myOnHideCallback;

  BarContainer(@NotNull BarType type, @NotNull TouchBar main, Map<Long, TouchBar> alts, Container parentComponent) {
    myParentComponent = parentComponent;
    myMain = main;
    myType = type;
    myKeyMask2Alt = alts;

    _updateTouchBarsParents();
  }

  void set(@NotNull TouchBar main, Map<Long, TouchBar> alts) {
    myMain = main;
    myKeyMask2Alt = alts;
    myCurrent = null;

    _updateTouchBarsParents();
  }

  void selectBarByKeyMask(long keyMask) {
    if (keyMask == 0) {
      myCurrent = myMain;
      return;
    }

    final TouchBar alt = myKeyMask2Alt == null ? null : myKeyMask2Alt.get(keyMask);
    if (alt != null)
      myCurrent = alt;
  }
  @NotNull TouchBar getMain() { return myMain; }

  TouchBar get() {
    if (myCurrent == null)
      myCurrent = myMain;
    return myCurrent;
  }

  void show() { TouchBarsManager.showContainer(this); }
  void hide() { TouchBarsManager.hideContainer(this); }

  @NotNull BarType getType() { return myType; }

  boolean isPopup() { return myType == BarType.POPUP; }
  boolean isDialog() { return myType == BarType.DIALOG || myType == BarType.MODAL_DIALOG; }
  boolean isNonModalDialog() { return myType == BarType.DIALOG; }
  boolean isModalDialog() { return myType == BarType.MODAL_DIALOG; }

  void setOnHideCallback(Runnable onHideCallback) { myOnHideCallback = onHideCallback; }
  void onHide() {
    if (myOnHideCallback != null)
      myOnHideCallback.run();
  }

  void setComponent(Component component) {
    myMain.setComponent(component);
    if (myKeyMask2Alt != null)
      myKeyMask2Alt.values().forEach(tb -> { tb.setComponent(component); });
  }

  void setOptionalContextActions(@Nullable ActionGroup actions, @NotNull String contextName) {
    if (actions == null)
      myMain.removeOptionalContextItems(contextName);
    else
      myMain.setOptionalContextItems(actions, contextName);
  }

  void setOptionalContextVisible(@Nullable String contextName) {
    myMain.setOptionalContextVisible(contextName);
  }

  Container getParentComponent() { return myParentComponent; }

  void release() {
    myMain.release();
    if (myKeyMask2Alt != null)
      myKeyMask2Alt.forEach((mask, bar)->bar.release());
    myKeyMask2Alt = null;
    myMain = TouchBar.EMPTY;
  }

  private void _updateTouchBarsParents() {
    myMain.setBarContainer(this);
    if (myKeyMask2Alt != null && !myKeyMask2Alt.isEmpty())
      myKeyMask2Alt.values().forEach(tb -> tb.setBarContainer(this));
  }
}
