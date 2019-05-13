/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

public class KeyProcessorContext {
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

  public void setFoundComponent(final JComponent foundComponent) {
    myFoundComponent = new WeakReference<>(foundComponent);
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
    return SoftReference.dereference(myFocusOwner);
  }

  public void setFocusOwner(final Component focusOwner) {
    myFocusOwner = new WeakReference<>(focusOwner);
  }

  public void setInputEvent(final KeyEvent e) {
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