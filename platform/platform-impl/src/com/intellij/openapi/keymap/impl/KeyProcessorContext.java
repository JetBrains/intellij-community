// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class KeyProcessorContext {
  private final List<AnAction> myActions = new ArrayList<>();
  private WeakReference<JComponent> myFoundComponent;
  private boolean myHasSecondStroke;

  private DataContext myDataContext;
  private boolean isModalContext;
  private WeakReference<Component> myFocusOwner;
  private KeyEvent myInputEvent;

  @NotNull
  List<AnAction> getActions() {
    return myActions;
  }

  @Nullable
  public JComponent getFoundComponent() {
    return SoftReference.dereference(myFoundComponent);
  }

  public void setFoundComponent(JComponent foundComponent) {
    myFoundComponent = new WeakReference<>(foundComponent);
  }

  public void setHasSecondStroke(boolean hasSecondStroke) {
    myHasSecondStroke = hasSecondStroke;
  }

  public boolean isHasSecondStroke() {
    return myHasSecondStroke;
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  public void setDataContext(@NotNull DataContext dataContext) {
    myDataContext = dataContext;
  }

  public boolean isModalContext() {
    return isModalContext;
  }

  public void setModalContext(boolean modalContext) {
    isModalContext = modalContext;
  }

  @Nullable
  public Component getFocusOwner() {
    return SoftReference.dereference(myFocusOwner);
  }

  public void setFocusOwner(Component focusOwner) {
    myFocusOwner = new WeakReference<>(focusOwner);
  }

  public void setInputEvent(KeyEvent e) {
    myInputEvent = e;
  }

  public KeyEvent getInputEvent() {
    return myInputEvent;
  }

  public void clear() {
    myInputEvent = null;
    myActions.clear();
    myFocusOwner = null;
    myDataContext = null;
  }
}