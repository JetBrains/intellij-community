// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Iterator;

final class StackTouchBars {
  private final ArrayDeque<BarContainer> myContainersStack = new ArrayDeque<>();
  private final TouchBarHolder myTouchBarHolder = new TouchBarHolder();

  private long myCurrentKeyMask;

  void updateKeyMask(long newMask) {
    if (myCurrentKeyMask != newMask) {
      synchronized (this) {
        myCurrentKeyMask = newMask;
        _setTouchBarFromTopContainer();
      }
    }
  }

  synchronized
  @Nullable
  TouchBar getTopTouchBar() {
    final BarContainer topContainer = myContainersStack.peek();
    return topContainer == null ? null : topContainer.get();
  }

  synchronized
  @Nullable
  BarContainer findTopProjectContainer(@NotNull Project project) {
    for (Iterator<BarContainer> it = myContainersStack.descendingIterator(); it.hasNext(); ) {
      BarContainer b = it.next();
      if (b.getProject() == project) {
        return b;
      }
    }
    return null;
  }

  synchronized void pop(@Nullable Condition<? super BarContainer> condition) {
    final BarContainer top = myContainersStack.peek();
    if (top == null) {
      return;
    }

    if (condition != null && !condition.value(top)) {
      return;
    }

    // System.out.println("removeContainer [POP]: " + top);
    myContainersStack.pop();
    _setTouchBarFromTopContainer();
  }

  synchronized void removeAll(@NotNull Collection<BarContainer> toErase) {
    myContainersStack.removeAll(toErase);
    _setTouchBarFromTopContainer();
  }

  synchronized void setTouchBarFromTopContainer() { _setTouchBarFromTopContainer(); }

  synchronized void showContainer(BarContainer bar) {
    if (bar == null) {
      return;
    }

    final BarContainer top = myContainersStack.peek();
    if (top == bar) {
      return;
    }

    // System.out.println("showContainer: " + bar);
    myContainersStack.remove(bar);
    myContainersStack.push(bar);
    _setTouchBarFromTopContainer();
  }

  synchronized void removeContainer(BarContainer tb) {
    if (tb == null || myContainersStack.isEmpty()) {
      return;
    }

    // System.out.println("removeContainer: " + tb);
    tb.onHide();

    BarContainer top = myContainersStack.peek();
    if (top == tb) {
      myContainersStack.pop();
      _setTouchBarFromTopContainer();
    }
    else {
      myContainersStack.remove(tb);
    }
  }

  private void _setTouchBarFromTopContainer() {
    if (myContainersStack.isEmpty()) {
      myTouchBarHolder.setTouchBar(null);
      return;
    }

    final BarContainer top = myContainersStack.peek();
    top.selectBarByKeyMask(myCurrentKeyMask);
    final TouchBar tb = top.get();
    if (tb != null && tb.isEmpty()) {
      myTouchBarHolder.setTouchBar(null);
      return;
    }

    myTouchBarHolder.setTouchBar(tb);
  }

  private static class TouchBarHolder {
    private TouchBar myCurrentBar;
    private TouchBar myNextBar;

    synchronized void setTouchBar(TouchBar bar) {
      final @NotNull Application application = ApplicationManager.getApplication();
      if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
        // don't create swing timers when unit-test mode
        return;
      }

      // the usual event sequence "focus lost -> show underlay bar -> focus gained" produces annoying flicker
      // use slightly deferred update to skip "showing underlay bar"
      // System.out.printf("schedule next TouchBar: %s | reason '%s'\n", bar, changeReason);
      // changeReason = null;

      myNextBar = bar;
      final Timer timer = new Timer(100, (event) -> _setNextTouchBar());
      timer.setRepeats(false);
      timer.start();
    }

    synchronized private void _setNextTouchBar() {
      if (myCurrentBar == myNextBar) {
        return;
      }

      // System.out.println("set next: " + myNextBar);
      if (myCurrentBar != null) {
        myCurrentBar.onHide();
      }
      myCurrentBar = myNextBar;
      if (myCurrentBar != null) {
        myCurrentBar.onBeforeShow();
      }
      NST.setTouchBar(myCurrentBar);
    }
  }
}
