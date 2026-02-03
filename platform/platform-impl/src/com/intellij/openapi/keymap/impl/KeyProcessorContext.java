// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.Project;
import com.intellij.reference.SoftReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

@ApiStatus.Internal
public final class KeyProcessorContext {
  private final List<AnAction> myActions = new ArrayList<>();
  private final List<AnAction> mySecondStrokeActions = new ArrayList<>();

  private WeakReference<JComponent> myFoundComponent;
  private DataContext myDataContext;
  private Project myProject;
  private boolean isModalContext;
  private WeakReference<Component> myFocusOwner;
  private KeyEvent myInputEvent;
  private Shortcut myShortcut;

  @NotNull List<AnAction> getActions() {
    return myActions;
  }

  @NotNull List<AnAction> getSecondStrokeActions() {
    return mySecondStrokeActions;
  }

  public @Nullable JComponent getFoundComponent() {
    return SoftReference.dereference(myFoundComponent);
  }

  public void setFoundComponent(JComponent foundComponent) {
    myFoundComponent = new WeakReference<>(foundComponent);
  }

  public DataContext getDataContext() {
    return myDataContext;
  }

  public void setDataContext(@NotNull DataContext dataContext) {
    myDataContext = dataContext;
  }

  public Project getProject() {
    return myProject;
  }

  public void setProject(@Nullable Project project) {
    myProject = project;
  }

  public boolean isModalContext() {
    return isModalContext;
  }

  public void setModalContext(boolean modalContext) {
    isModalContext = modalContext;
  }

  public @Nullable Component getFocusOwner() {
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

  public Shortcut getShortcut() {
    return myShortcut;
  }

  public void setShortcut(Shortcut shortcut) {
    myShortcut = shortcut;
  }

  public void clear() {
    myInputEvent = null;
    myShortcut = null;
    myActions.clear();
    mySecondStrokeActions.clear();
    myFocusOwner = null;
    myDataContext = null;
    myProject = null;
  }
}