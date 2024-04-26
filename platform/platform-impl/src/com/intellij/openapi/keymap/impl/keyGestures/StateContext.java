// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

final class StateContext {
  KeyEvent actionKey;
  KeyStroke actionShortcut;
  KeyboardGestureAction.ModifierType modifierType;
  Presentation actionPresentation;
  public String actionPlace;
  DataContext dataContext;

  Component focusOwner;
  KeyEvent keyToProcess;
  boolean isModal;

  @Override
  public @NonNls String toString() {
    return
      "actionKey=" + actionKey + "\n"
      + "actionShortcut=" + actionShortcut + "\n"
      + "modifierType=" + modifierType + "\n"
      + "actionPresentation=" + actionPresentation + "\n"
      + "actionPlace=" + actionPlace + "\n"
      + "dataContext=" + dataContext + "\n"
      + "focusOwner=" + focusOwner + "\n"
      + "keyToProcess=" + keyToProcess + "\n"
      + "isModal=" + isModal + "\n";
  }
}