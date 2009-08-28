package com.intellij.openapi.keymap.impl.keyGestures;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardGestureAction;
import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.*;

class StateContext {
  KeyEvent actionKey;
  KeyStroke actionShortcut;
  KeyboardGestureAction.ModifierType modifierType;
  Presentation actionPresentation;
  public String actionPlace;
  DataContext dataContext;

  Component focusOwner;
  KeyEvent keyToProcess;
  boolean isModal;
}