// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.impl.IdeFocusManagerHeadless;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
* @author Konstantin Bulenkov
*/
final class HeadlessDialog implements AbstractDialog {
  private final @NotNull DialogWrapper myWrapper;
  private @NlsContexts.DialogTitle String myTitle;

  HeadlessDialog(@NotNull DialogWrapper wrapper) {
    myWrapper = wrapper;
  }

  @Override
  public void setUndecorated(boolean undecorated) {
  }

  @Override
  public void addMouseListener(MouseListener listener) {
  }

  @Override
  public void addMouseMotionListener(MouseMotionListener listener) {
  }

  @Override
  public void addKeyListener(KeyListener listener) {
  }

  @Override
  public void setModal(boolean b) {
  }

  @Override
  public void toFront() {
  }

  @Override
  public void setContentPane(Container content) {
  }

  @Override
  public void centerInParent() {
  }

  @Override
  public void toBack() {
  }

  @Override
  public JRootPane getRootPane() {
    return null;
  }

  @Override
  public void remove(Component root) {
  }

  @Override
  public Container getContentPane() {
    return null;
  }

  @Override
  public void validate() {
  }

  @Override
  public void repaint() {
  }

  @Override
  public Window getOwner() {
    return null;
  }

  @Override
  public JDialog getWindow() {
    return null;
  }

  @Override
  public Dimension getSize() {
    return null;
  }

  @Override
  public String getTitle() {
    return myTitle;
  }

  @Override
  public void pack() {
  }

  @Override
  public Dimension getPreferredSize() {
    return null;
  }

  @Override
  public boolean isVisible() {
    return false;
  }

  @Override
  public boolean isShowing() {
    return false;
  }

  @Override
  public void setSize(int width, int height) {
  }

  @Override
  public void setTitle(String title) {
    myTitle = title;
  }

  @Override
  public boolean isResizable() {
    return false;
  }

  @Override
  public void setResizable(boolean resizable) {
  }

  @Override
  public @NotNull Point getLocation() {
    return new Point(0,0);
  }

  @Override
  public void setLocation(@NotNull Point p) {
  }

  @Override
  public void setLocation(int x, int y) {
  }

  @Override
  public boolean isModal() {
    return false;
  }

  @Override
  public void setModalityType(Dialog.ModalityType modalityType) {
  }

  @Override
  public Dialog.ModalityType getModalityType() {
    return null;
  }

  @Override
  public void show() {
    myWrapper.close(DialogWrapper.OK_EXIT_CODE);
  }

  @Override
  public @NotNull IdeFocusManager getFocusManager() {
    return new IdeFocusManagerHeadless();
  }

  @Override
  public void dispose() {
  }
}
