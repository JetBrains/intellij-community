/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.impl.ActionProcessor;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.keymap.impl.KeyState;
import com.intellij.openapi.util.registry.Registry;

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


  final Timer myHoldTimer = new Timer(1200, new ActionListener() {
    public void actionPerformed(final ActionEvent e) {
    }
  });

  final Timer myDblClickTimer = new Timer(Registry.intValue("actionSystem.keyGestureDblClickTime"), new ActionListener() {
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

  private Shortcut getCurrentShortcut() {
    return KeyboardModifierGestureShortuct.newInstance(myContext.modifierType, myContext.actionShortcut);
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
    public AnActionEvent createEvent(final InputEvent inputEvent, final DataContext context, final String place, final Presentation presentation,
                                     final ActionManager manager) {
      myContext.actionPresentation = presentation;
      myContext.actionPlace = place;
      return myState.createActionEvent();
    }

    public void onUpdatePassed(final InputEvent inputEvent, final AnAction action, final AnActionEvent actionEvent) {
    }

    public void performAction(final InputEvent e, final AnAction action, final AnActionEvent actionEvent) {
      final boolean isGestureAction = action instanceof KeyboardGestureAction;
      actionEvent.accept(new AnActionEventVisitor() {
        @Override
        public void visitGestureInitEvent(final AnActionEvent anActionEvent) {
          if (isGestureAction) {
            execute(anActionEvent, action, e);
          }
        }

        @Override
        public void visitGesturePerformedEvent(final AnActionEvent anActionEvent) {
          execute(anActionEvent, action, e);
        }

        @Override
        public void visitGestureFinishEvent(final AnActionEvent anActionEvent) {
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
