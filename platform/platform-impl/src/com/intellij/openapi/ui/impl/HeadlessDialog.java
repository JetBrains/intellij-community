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
package com.intellij.openapi.ui.impl;

import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.FocusTrackback;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
* @author Konstantin Bulenkov
*/
@SuppressWarnings("ConstantConditions")
class HeadlessDialog implements AbstractDialog {
  public void setUndecorated(boolean undecorated) {
  }

  public void addMouseListener(MouseListener listener) {
  }

  public void addMouseMotionListener(MouseMotionListener listener) {
  }

  public void addKeyListener(KeyListener listener) {
  }

  public void setModal(boolean b) {
  }

  public void toFront() {
  }

  public void setContentPane(Container content) {
  }

  public void centerInParent() {
  }

  public void toBack() {
  }

  public JRootPane getRootPane() {
    return null;
  }

  public void remove(Component root) {
  }

  public Container getContentPane() {
    return null;
  }

  public void validate() {
  }

  public void repaint() {
  }

  public Window getOwner() {
    return null;
  }

  public JDialog getWindow() {
    return null;
  }

  public Dimension getSize() {
    return null;
  }

  public String getTitle() {
    return null;
  }

  public void pack() {
  }

  public Dimension getPreferredSize() {
    return null;
  }

  public boolean isVisible() {
    return false;
  }

  public boolean isShowing() {
    return false;
  }

  public void setSize(int width, int height) {
  }

  public void setTitle(String title) {
  }

  public boolean isResizable() {
    return false;
  }

  public void setResizable(boolean resizable) {
  }

  public Point getLocation() {
    return null;
  }

  public void setLocation(Point p) {
  }

  public void setLocation(int x, int y) {
  }

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

  public void show() {
  }

  public IdeFocusManager getFocusManager() {
    return null;
  }

  public FocusTrackback getFocusTrackback() {
    return null;
  }

  public void dispose() {
  }
}
