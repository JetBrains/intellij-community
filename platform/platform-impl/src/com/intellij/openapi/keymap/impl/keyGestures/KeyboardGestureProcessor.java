// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public final class KeyboardGestureProcessor {
  final IdeKeyEventDispatcher myDispatcher;
  final StateContext myContext = new StateContext();

  final KeyGestureState myWaitForStart = new KeyGestureState.WaitForStart(this);
  final KeyGestureState myModifierPressed = new KeyGestureState.ModifierPressed(this);
  final KeyGestureState myWaitForDblClick = new KeyGestureState.WaitForDblClick(this);
  final KeyGestureState myWaitForAction = new KeyGestureState.WaitForAction(this);
  final KeyGestureState myWaitForActionEnd = new KeyGestureState.WaitForActionEnd(this);
  final KeyGestureState myFinish = new KeyGestureState.ProcessFinish(this);

  KeyGestureState myState = myWaitForStart;

  final Timer myHoldTimer = TimerUtil.createNamedTimer("Keyboard hold", 1200, e -> { });

  final Timer myDblClickTimer = TimerUtil.createNamedTimer("Double click", SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650), new ActionListener() {
    @Override
    public void actionPerformed(final ActionEvent e) {
      myState.processDblClickTimer();
    }
  });
  private final ActionProcessor myActionProcessor = new MyActionProcessor();

  public KeyboardGestureProcessor(final IdeKeyEventDispatcher dispatcher) {
    myDispatcher = dispatcher;
  }

  public boolean process() {
    boolean wasNotInWaitState = myState != myWaitForStart;

    if (Boolean.getBoolean("ide.debugMode") && wasNotInWaitState) {
      System.out.println("-- key gesture context: before process, state=" + myState);
      System.out.println(myContext);
    }

    myContext.keyToProcess = myDispatcher.getContext().getInputEvent();
    myContext.isModal = myDispatcher.getContext().isModalContext();
    myContext.dataContext = myDispatcher.getContext().getDataContext();

    boolean result = myState.process();

    if (Boolean.getBoolean("ide.debugMode") && (wasNotInWaitState || myState != myWaitForStart)) {
      System.out.println("-- key gesture context: after process, state=" + myState);
      System.out.println(myContext);
    }

    return result;
  }

  public boolean processInitState() {
    if (!Boolean.getBoolean("actionSystem.keyGestures.enabled")) {
      return false;
    }

    myContext.focusOwner = myDispatcher.getContext().getFocusOwner();
    return process();
  }

  void executeAction() {
    myDispatcher.updateCurrentContext(myContext.focusOwner, getCurrentShortcut());
    myDispatcher.processAction(myContext.keyToProcess, myActionProcessor);
  }

  private @NotNull Shortcut getCurrentShortcut() {
    return KeyboardModifierGestureShortcut.newInstance(myContext.modifierType, myContext.actionShortcut);
  }

  void setState(KeyGestureState state) {
    final boolean isGestureProcessingState = myDispatcher.getState() == KeyState.STATE_KEY_GESTURE_PROCESSOR;
    if (state == myWaitForStart) {
      myContext.actionKey = null;
      if (isGestureProcessingState) {
        myDispatcher.setState(KeyState.STATE_INIT);
      }
    }
    else if (state == myWaitForAction) {
      myDispatcher.setState(KeyState.STATE_KEY_GESTURE_PROCESSOR);
    }
    myState = state;
  }

  private final class MyActionProcessor extends ActionProcessor {
    @Override
    public @NotNull AnActionEvent createEvent(@NotNull InputEvent inputEvent,
                                              @NotNull DataContext context,
                                              @NotNull String place,
                                              @NotNull Presentation presentation,
                                              @NotNull ActionManager manager) {
      myContext.actionPresentation = presentation;
      myContext.actionPlace = place;
      return myState.createActionEvent();
    }

    @Override
    public void performAction(@NotNull InputEvent inputEvent, @NotNull AnAction action, @NotNull AnActionEvent event) {
      Runnable runnable = () -> super.performAction(inputEvent, action, event);
      event.accept(new AnActionEventVisitor() {
        @Override
        public void visitGestureInitEvent(@NotNull AnActionEvent anActionEvent) {
          if (action instanceof KeyboardGestureAction) {
            runnable.run();
          }
        }

        @Override
        public void visitGesturePerformedEvent(@NotNull AnActionEvent anActionEvent) {
          runnable.run();
        }

        @Override
        public void visitGestureFinishEvent(@NotNull AnActionEvent anActionEvent) {
          if (action instanceof KeyboardGestureAction) {
            runnable.run();
          }
        }
      });
    }
  }
}
