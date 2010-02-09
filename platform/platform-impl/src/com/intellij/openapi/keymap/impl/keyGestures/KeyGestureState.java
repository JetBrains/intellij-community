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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;

import javax.swing.*;
import java.awt.event.KeyEvent;

abstract class KeyGestureState {

  final KeyboardGestureProcessor myProcessor;

  final StateContext myContext;

  public KeyGestureState(final KeyboardGestureProcessor processor) {
    myProcessor = processor;
    myContext = processor.myContext;
  }

  abstract boolean process();

  public void processDblClickTimer() {
    myProcessor.setState(myProcessor.myWaitForStart);
    myProcessor.myDblClickTimer.stop();
  }

  @Override
  public String toString() {
    return getClass().getName();
  }

  boolean isPureModifierEvent(int eventType) {
    final KeyEvent event = myContext.keyToProcess;
    if (event.getID() != eventType) return false;

    return event.getKeyCode() == KeyEvent.VK_CONTROL
        || event.getKeyCode() == KeyEvent.VK_ALT
        || event.getKeyCode() == KeyEvent.VK_SHIFT
        || event.getKeyCode() == KeyEvent.VK_META;
  }

  public AnActionEvent createActionEvent() {
    throw new IllegalStateException(getClass().getName());
  }

  static class WaitForStart extends KeyGestureState {

    WaitForStart(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) return false;


      myProcessor.myContext.actionKey = myProcessor.myContext.keyToProcess;
      myProcessor.setState(myProcessor.myModifierPressed);
      myProcessor.myHoldTimer.start();

      return false;
    }
  }

  static class ModifierPressed extends KeyGestureState {

    ModifierPressed(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) {
        myProcessor.myHoldTimer.stop();
        myContext.actionKey = null;
        myProcessor.setState(myProcessor.myWaitForDblClick);

        myProcessor.myDblClickTimer.start();
        myContext.actionKey = myContext.keyToProcess;

        return false;
      } else if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.actionKey = myContext.keyToProcess;
        return false;
      }

      myProcessor.setState(myProcessor.myWaitForStart);
      return false;
    }
  }

  static class WaitForDblClick extends KeyGestureState {

    WaitForDblClick(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      myProcessor.myDblClickTimer.stop();

      if (isPureModifierEvent(KeyEvent.KEY_RELEASED)) return false;

      if (!isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myProcessor.setState(myProcessor.myWaitForStart);
        return false;
      }

      myContext.actionKey = myContext.keyToProcess;
      myProcessor.setState(myProcessor.myWaitForAction);

      myContext.actionShortcut = KeyStroke.getKeyStrokeForEvent(myContext.actionKey);
      myContext.modifierType = KeyboardGestureAction.ModifierType.dblClick;

      myProcessor.executeAction();

      return true;
    }

  }

  static class WaitForAction extends KeyGestureState {

    WaitForAction(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (isPureModifierEvent(KeyEvent.KEY_PRESSED)) {
        myContext.actionKey = myContext.keyToProcess;
        return true;
      }

      if (myContext.keyToProcess.getID() == KeyEvent.KEY_RELEASED && myContext.keyToProcess.getKeyChar() == KeyEvent.CHAR_UNDEFINED) {
        final int pressedModifiers = myContext.keyToProcess.getKeyCode() & myContext.actionKey.getModifiersEx();
        if (pressedModifiers == 0) {
          myProcessor.setState(myProcessor.myFinish);
          return myProcessor.myState.process();
        }
      }

      if (myContext.keyToProcess.getID() == KeyEvent.KEY_PRESSED) {
        myContext.actionKey = myContext.keyToProcess;
        myProcessor.setState(myProcessor.myWaitForActionEnd);
        return true;
      }

      return false;
    }

    @Override
    public AnActionEvent createActionEvent() {
      return new GestureActionEvent.Init(myProcessor);
    }
  }

  static class ProcessFinish extends KeyGestureState {
    ProcessFinish(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      myProcessor.executeAction();
      myProcessor.setState(myProcessor.myWaitForStart);
      return false;
    }

    @Override
    public AnActionEvent createActionEvent() {
      return new GestureActionEvent.Finish(myProcessor);
    }
  }

  static class WaitForActionEnd extends KeyGestureState {

    WaitForActionEnd(final KeyboardGestureProcessor processor) {
      super(processor);
    }

    boolean process() {
      if (myContext.keyToProcess.getID() == KeyEvent.KEY_RELEASED) {
        myProcessor.executeAction();
        myProcessor.setState(myProcessor.myWaitForAction);
        return true;
      }

      return false;
    }

    @Override
    public AnActionEvent createActionEvent() {
      return new GestureActionEvent.PerformAction(myProcessor);
    }
  }

}