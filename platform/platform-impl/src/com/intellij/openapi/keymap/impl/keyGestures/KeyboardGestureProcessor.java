// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SystemProperties;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;

public class KeyboardGestureProcessor {
  IdeKeyEventDispatcher myDispatcher;

  StateContext myContext = new StateContext();

  final KeyGestureState myWaitForStart = new KeyGestureState.WaitForStart(this);
  final KeyGestureState myModifierPressed = new KeyGestureState.ModifierPressed(this);
  final KeyGestureState myWaitForDblClick = new KeyGestureState.WaitForDblClick(this);
  final KeyGestureState myWaitForAction = new KeyGestureState.WaitForAction(this);
  final KeyGestureState myWaitForActionEnd = new KeyGestureState.WaitForActionEnd(this);
  final KeyGestureState myFinish = new KeyGestureState.ProcessFinish(this);

  KeyGestureState myState = myWaitForStart;


  final Timer myHoldTimer = UIUtil.createNamedTimer("Keyboard hold", 1200, e -> { });

  final Timer myDblClickTimer = UIUtil.createNamedTimer("Double click", SystemProperties.getIntProperty("actionSystem.keyGestureDblClickTime", 650), new ActionListener() {
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

    if (Registry.is("ide.debugMode") && wasNotInWaitState) {
      System.out.println("-- key gesture context: before process, state=" + myState);
      System.out.println(myContext);
    }

    myContext.keyToProcess = myDispatcher.getContext().getInputEvent();
    myContext.isModal = myDispatcher.getContext().isModalContext();
    myContext.dataContext = myDispatcher.getContext().getDataContext();

    boolean result = myState.process();

    if (Registry.is("ide.debugMode") && (wasNotInWaitState || myState != myWaitForStart)) {
      System.out.println("-- key gesture context: after process, state=" + myState);
      System.out.println(myContext);
    }

    return result;
  }

  public boolean processInitState() {
    if (!Registry.is("actionSystem.keyGestures.enabled")) return false;

    myContext.focusOwner = myDispatcher.getContext().getFocusOwner();
    return process();
  }

  void executeAction() {
    myDispatcher.updateCurrentContext(myContext.focusOwner, getCurrentShortcut(), myContext.isModal);
    myDispatcher.processAction(myContext.keyToProcess, myActionProcessor);
  }

  @NotNull
  private Shortcut getCurrentShortcut() {
    return KeyboardModifierGestureShortcut.newInstance(myContext.modifierType, myContext.actionShortcut);
  }

  void setState(KeyGestureState state) {
    final boolean isGestureProcessingState = myDispatcher.getState() == KeyState.STATE_KEY_GESTURE_PROCESSOR;
    if (state == myWaitForStart) {
      myContext.actionKey = null;
      if (isGestureProcessingState) {
        myDispatcher.setState(KeyState.STATE_INIT);
      }
    } else if (state == myWaitForAction) {
      myDispatcher.setState(KeyState.STATE_KEY_GESTURE_PROCESSOR);
    }
    myState = state;
  }

  private class MyActionProcessor implements ActionProcessor {
    @Override
    @NotNull
    public AnActionEvent createEvent(final InputEvent inputEvent, @NotNull final DataContext context, @NotNull final String place, @NotNull final Presentation presentation,
                                     @NotNull final ActionManager manager) {
      myContext.actionPresentation = presentation;
      myContext.actionPlace = place;
      return myState.createActionEvent();
    }

    @Override
    public void onUpdatePassed(final InputEvent inputEvent, @NotNull final AnAction action, @NotNull final AnActionEvent actionEvent) {
    }

    @Override
    public void performAction(final InputEvent e, @NotNull final AnAction action, @NotNull final AnActionEvent actionEvent) {
      final boolean isGestureAction = action instanceof KeyboardGestureAction;
      actionEvent.accept(new AnActionEventVisitor() {
        @Override
        public void visitGestureInitEvent(@NotNull final AnActionEvent anActionEvent) {
          if (isGestureAction) {
            execute(anActionEvent, action, e);
          }
        }

        @Override
        public void visitGesturePerformedEvent(@NotNull final AnActionEvent anActionEvent) {
          execute(anActionEvent, action, e);
        }

        @Override
        public void visitGestureFinishEvent(@NotNull final AnActionEvent anActionEvent) {
          if (isGestureAction) {
            execute(anActionEvent, action, e);
          }
        }
      });
    }

    private void execute(final AnActionEvent anActionEvent, final AnAction action, final InputEvent e) {
      action.actionPerformed(anActionEvent);
      e.consume();
    }
  }

}
