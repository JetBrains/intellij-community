// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Collection;

public class StackTouchBars {
  private final ArrayDeque<BarContainer> myContainersStack = new ArrayDeque<>();
  private final TouchBarHolder myTouchBarHolder = new TouchBarHolder();

  private long myCurrentKeyMask;

  // static String changeReason;  // for debugging only

  void updateKeyMask(long newMask) {
    if (myCurrentKeyMask != newMask) {
      synchronized (this) {
        // System.out.printf("change current mask: 0x%X -> 0x%X\n", myCurrentKeyMask, e.getModifiersEx());
        // changeReason = String.format("change current mask: 0x%X -> 0x%X", myCurrentKeyMask, newMask);
        myCurrentKeyMask = newMask;
        _setTouchBarFromTopContainer();
      }
    }
  }

  synchronized
  @Nullable TouchBar getTopTouchBar() {
    final BarContainer topContainer = myContainersStack.peek();
    return topContainer == null ? null : topContainer.get();
  }

  synchronized
  void pop(@Nullable Condition<BarContainer> condition) {
    final BarContainer top = myContainersStack.peek();
    if (top == null)
      return;

    if (condition != null && !condition.value(top))
      return;

    // System.out.println("removeContainer [POP]: " + top);
    myContainersStack.pop();
    _setTouchBarFromTopContainer();
  }

  synchronized
  void removeAll(@NotNull Collection<BarContainer> toErase) {
    myContainersStack.removeAll(toErase);
    _setTouchBarFromTopContainer();
  }

  synchronized
  void setTouchBarFromTopContainer() { _setTouchBarFromTopContainer(); }

  synchronized
  void showContainer(BarContainer bar) {
    if (bar == null)
      return;

    final BarContainer top = myContainersStack.peek();
    if (top == bar)
      return;

    // System.out.println("showContainer: " + bar);
    myContainersStack.remove(bar);
    myContainersStack.push(bar);
    _setTouchBarFromTopContainer();
  }

  synchronized
  void removeContainer(BarContainer tb) {
    if (tb == null || myContainersStack.isEmpty())
      return;

    // System.out.println("removeContainer: " + tb);
    tb.onHide();

    BarContainer top = myContainersStack.peek();
    if (top == tb) {
      myContainersStack.pop();
      _setTouchBarFromTopContainer();
    } else {
      myContainersStack.remove(tb);
    }
  }

  synchronized
  void elevateContainer(BarContainer bar) {
    if (bar == null)
      return;

    final BarContainer top = myContainersStack.peek();
    if (top == bar)
      return;

    final boolean preserveTop = top != null && (top.isPopup() || top.isDialog() || top.get().isManualClose());
    if (preserveTop) {
      myContainersStack.remove(bar);
      myContainersStack.remove(top);
      myContainersStack.push(bar);
      myContainersStack.push(top);
    } else {
      myContainersStack.remove(bar);
      myContainersStack.push(bar);
      _setTouchBarFromTopContainer();
    }
  }

  private void _setTouchBarFromTopContainer() {
    if (myContainersStack.isEmpty()) {
      myTouchBarHolder.setTouchBar(null);
      return;
    }

    final BarContainer top = myContainersStack.peek();
    top.selectBarByKeyMask(myCurrentKeyMask);
    myTouchBarHolder.setTouchBar(top.get());
  }

  private static class TouchBarHolder {
    private TouchBar myCurrentBar;
    private TouchBar myNextBar;

    synchronized void setTouchBar(TouchBar bar) {
      // the usual event sequence "focus lost -> show underlay bar -> focus gained" produces annoying flicker
      // use slightly deferred update to skip "showing underlay bar"
      // System.out.printf("schedule next TouchBar: %s | reason '%s'\n", bar, changeReason);
      // changeReason = null;

      myNextBar = bar;
      final Timer timer = new Timer(100, (event)->{
        _setNextTouchBar();
      });
      timer.setRepeats(false);
      timer.start();
    }

    synchronized void updateCurrent() {
      if (myCurrentBar != null)
        myCurrentBar.updateActionItems();
    }

    synchronized private void _setNextTouchBar() {
      if (myCurrentBar == myNextBar) {
        return;
      }

      // System.out.println("set next: " + myNextBar);
      if (myCurrentBar != null)
        myCurrentBar.onHide();
      myCurrentBar = myNextBar;
      if (myCurrentBar != null)
        myCurrentBar.onBeforeShow();
      NST.setTouchBar(myCurrentBar);
    }
  }
}
