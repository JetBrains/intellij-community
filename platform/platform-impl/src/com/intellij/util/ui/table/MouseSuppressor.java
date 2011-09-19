/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.ui.table;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.*;

/**
* @author Konstantin Bulenkov
*/
class MouseSuppressor implements MouseListener, MouseWheelListener, MouseMotionListener {
  private static void handle(InputEvent e) {
    e.consume();
  }

  public static void install(@NotNull JComponent component) {
    component.addMouseListener(new MouseSuppressor());
  }

  @Override
  public void mouseClicked(MouseEvent e) {handle(e);}

  @Override
  public void mousePressed(MouseEvent e) {handle(e);}

  @Override
  public void mouseReleased(MouseEvent e) {handle(e);}

  @Override
  public void mouseEntered(MouseEvent e) {handle(e);}

  @Override
  public void mouseExited(MouseEvent e) {handle(e);}

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {handle(e);}

  @Override
  public void mouseDragged(MouseEvent e) {handle(e);}

  @Override
  public void mouseMoved(MouseEvent e) {handle(e);}
}
