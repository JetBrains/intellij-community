/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
* @author Konstantin Bulenkov
*/
interface AbstractDialog extends Disposable {
  void setUndecorated(boolean undecorated);

  void addMouseListener(MouseListener listener);

  void addMouseMotionListener(MouseMotionListener listener);

  void addKeyListener(KeyListener listener);

  @Deprecated // Use setModalityType instead
  void setModal(boolean b);

  void toFront();

  void setContentPane(Container content);

  void centerInParent();

  void toBack();

  JRootPane getRootPane();

  void remove(Component root);

  Container getContentPane();

  void validate();

  void repaint();

  Window getOwner();

  JDialog getWindow();

  Dimension getSize();

  String getTitle();

  void pack();

  Dimension getPreferredSize();

  boolean isVisible();

  boolean isShowing();

  void setSize(int width, int height);

  void setTitle(String title);

  boolean isResizable();

  void setResizable(boolean resizable);

  @NotNull
  Point getLocation();

  void setLocation(@NotNull Point p);

  void setLocation(int x, int y);

  @Deprecated // use getModalityTypeInstead
  boolean isModal();

  void setModalityType(Dialog.ModalityType modalityType);

  Dialog.ModalityType getModalityType();

  void show();

  @NotNull
  IdeFocusManager getFocusManager();

}
