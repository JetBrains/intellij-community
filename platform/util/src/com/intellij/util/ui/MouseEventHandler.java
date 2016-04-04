/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import java.awt.event.*;

/**
 * @author Sergey.Malenkov
 */
public abstract class MouseEventHandler implements MouseListener, MouseMotionListener, MouseWheelListener {
  protected abstract void handle(MouseEvent event);

  @Override
  public void mousePressed(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseEntered(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseExited(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    handle(event);
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent event) {
    handle(event);
  }
}
