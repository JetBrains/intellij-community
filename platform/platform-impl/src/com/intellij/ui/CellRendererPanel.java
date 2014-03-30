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
package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Cell renderer CPU optimization.
 * @see javax.swing.table.DefaultTableCellRenderer#invalidate()
 *
 * @author gregsh
 */
public class CellRendererPanel extends JPanel {

  // property change support ----------------
  protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
  }

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
  }

  // isOpaque() optimization ----------------
  public boolean isOpaque() {
    return false;
  }

  @Override
  protected void paintComponent(Graphics g) {
  }

  // BEGIN no validation methods --------------
  @Override
  public void doLayout() {
    if (getComponentCount() != 1) return;
    getComponent(0).setBounds(0, 0, getWidth(), getHeight());
  }

  @Override
  public Dimension getPreferredSize() {
    if (getComponentCount() != 1) return super.getPreferredSize();
    return getComponent(0).getPreferredSize();
  }

  public void invalidate() {
  }

  public void validate() {
    doLayout();
  }

  public void revalidate() {
  }

  public void repaint(long tm, int x, int y, int width, int height) {
  }

  public void repaint(Rectangle r) {
  }

  public void repaint() {
  }

// END no validation methods --------------
}
