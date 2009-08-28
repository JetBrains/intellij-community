package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class KeyProcessorContext {
  private final ArrayList<AnAction> myActions = new ArrayList<AnAction>();
  private WeakReference<JComponent> myFoundComponent;
  private boolean myHasSecondStroke;

  private DataContext myDataContext;
  private boolean isModalContext;
  private WeakReference<Component> myFocusOwner;
  private KeyEvent myInputEvent;

  public ArrayList<AnAction> getActions() {
    return myActions;
  }

  @Nullable
  public JComponent getFoundComponent() {
    return myFoundComponent != null ? myFoundComponent.get() : null;
  }

  public void setFoundComponent(final JComponent foundComponent) {
    myFoundComponent = new WeakReference<JComponent>(foundComponent);
  }

  public void setHasSecondStroke(final boolean hasSecondStroke) {
    myHasSecondStroke = hasSecondStroke;
  }

  public boolean isHasSecondStroke() {
    return myHasSecondStroke;
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  public void setDataContext(final DataContext dataContext) {
    myDataContext = dataContext;
  }

  public boolean isModalContext() {
    return isModalContext;
  }

  public void setModalContext(final boolean modalContext) {
    isModalContext = modalContext;
  }

  @Nullable
  public Component getFocusOwner() {
    return myFocusOwner != null ? myFocusOwner.get() : null;
  }

  public void setFocusOwner(final Component focusOwner) {
    myFocusOwner = new WeakReference<Component>(focusOwner);
  }

  public void setInputEvent(final KeyEvent e) {
    myInputEvent = e;
  }

  public KeyEvent getInputEvent() {
    return myInputEvent;
  }
}