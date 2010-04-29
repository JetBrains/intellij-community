/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

abstract public class AbstractToolTipHandler <KeyType, ComponentType extends JComponent>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.AbstractToolTipHandler");

  protected final ComponentType myComponent;
  protected final CellRendererPane myRendererPane = new CellRendererPane();
  private final TipComponent myTipComponent;

  private Hint myHint;
  /**
   * A sort of unique key that identify the cell at point
   */
  private KeyType myKey;
  protected BufferedImage myImage;

  protected AbstractToolTipHandler(@NotNull final ComponentType component) {
    myComponent = component;
    myComponent.add(myRendererPane);
    myComponent.validate();

    myTipComponent = new TipComponent();
    myComponent.addMouseListener(
      new MouseListener() {
        public void mouseEntered(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseExited(MouseEvent e) {
          hideHint();
        }

        public void mouseClicked(MouseEvent e) {}

        public void mousePressed(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseReleased(MouseEvent e) {
          handleMouseEvent(e);
        }
      }
    );

    myComponent.addMouseMotionListener(
      new MouseMotionListener() {
        public void mouseDragged(MouseEvent e) {
          handleMouseEvent(e);
        }

        public void mouseMoved(MouseEvent e) {
          handleMouseEvent(e);
        }
      }
    );

    myComponent.addFocusListener(
      new FocusAdapter() {
        public void focusLost(FocusEvent e) {
          hideHint();
        }

        public void focusGained(FocusEvent e) {
          repaintHint();
        }
      }
    );

    myComponent.addComponentListener(
      new ComponentAdapter() {
        public void componentHidden(ComponentEvent e){
          hideHint();
        }

        public void componentMoved(ComponentEvent e) {
          hideHint();
        }
      }
    );
    myComponent.addHierarchyListener(
      new HierarchyListener() {
        public void hierarchyChanged(HierarchyEvent e){
          hideHint();
        }
      }
    );
  }

  private class TipComponent extends JComponent {
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    public Dimension getPreferredSize() {
      return new Dimension(myImage.getWidth(), myImage.getHeight());
    }

    public void paint(Graphics g) {
      g.drawImage(myImage, 0, 0, null);
    }
  }

  /**
   * @return "key" that can unique identify some cell at the specified <code>point</code>.
   * <code>null</code> means that the <code>point</code> doesn't correspond to
   * any cell. Please note that the <code>point</code> is in coordinate system
   * of the component which was passed into the class constructor.
   */
  protected abstract KeyType getCellKeyForPoint(Point point);

  /**
   * Hides tool tip
   */
  protected final void hideHint() {
    if (myHint != null) {
      myHint.hide();
      myHint = null;
    }
  }

  private void handleMouseEvent(MouseEvent e){
    Object oldKey = myKey;
    myKey = getCellKeyForPoint(e.getPoint());
    if(myKey == null || !myComponent.isShowing()){
      hideHint();
      return;
    }

    Point location = createToolTipImage(myKey);

    if (location == null) {
      hideHint();
    }
    else if (myHint == null) {
      show(location);
    }
    else if (!Comparing.equal(oldKey, myKey)){
      hideHint();
      show(location);
    }
  }

  private void show(Point location) {
    LOG.assertTrue(myHint == null);

    if (!myComponent.isShowing()) {
      return;
    }

    JLayeredPane layeredPane = myComponent.getRootPane().getLayeredPane();
    Point layeredPanePoint = SwingUtilities.convertPoint(myComponent, location.x + myTipComponent.getPreferredSize().width, 0, layeredPane);
    boolean fitIntoLayeredPane = layeredPanePoint.x < layeredPane.getWidth();

    if (fitIntoLayeredPane) {
      myHint = new LightweightHint(myTipComponent);
    } else {
      MenuElement[] selectedPath = MenuSelectionManager.defaultManager().getSelectedPath();
      if (selectedPath.length > 0) {
        // do not show heavyweight hints when menu is shown to avoid their overlapping
        return;
      }
      myHint = new HeavyweightHint(myTipComponent, false);
    }
    myHint.show(myComponent, location.x, location.y, myComponent);
  }

  protected void repaintHint() {
    if (myHint != null && myKey != null && myComponent.isShowing()) {
      createToolTipImage(myKey);
      myTipComponent.repaint();
    }
  }

  /**
   * @return point (in myComponent coordinates) for the cell which is specified
   * by <code>key</code>. The method can return <code>null</code> in case if
   * <code>key</code> does not define any valid cell.
   */
  protected Point createToolTipImage(@NotNull KeyType key) {
    Component rComponent;
    rComponent = getRendererComponent(key);

    if (!(rComponent instanceof JComponent)) {
      return null;
    }

    Rectangle cellBounds = getCellBounds(key, rComponent);
    if (cellBounds == null) return null;
    Point cellLocation = cellBounds.getLocation();
    Rectangle visibleRect = getVisibleRect(key);
    int height = cellBounds.height;

    if (visibleRect.contains(cellLocation.x + cellBounds.width, cellLocation.y + height)) return null;

    Point visibleRightTop = new Point(visibleRect.x + visibleRect.width, cellLocation.y);
    int rightLimit = cellLocation.x + cellBounds.width;
    int width = rightLimit - visibleRightTop.x;
    if (width <= 0 || height <= 0) return null;

    myImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

    Graphics2D g = myImage.createGraphics();
    g.setClip(null);
    g.setColor(myComponent.getBackground());
    g.fillRect(0, 0, width, height);
    g.translate(-(visibleRect.x + visibleRect.width - cellLocation.x), 0);
    //rComponent.paint(g);
    doPaintTooltipImage(rComponent, cellBounds, height, g, key);

    g.translate((visibleRect.x + visibleRect.width - cellLocation.x), 0);
    g.setColor(Color.black);
    int rightX = myImage.getWidth() - 1;
    UIUtil.drawLine(g, 0, 0, rightX, 0);
    UIUtil.drawLine(g, rightX, 0, rightX, height);
    UIUtil.drawLine(g, 0, height - 1, rightX, height - 1);
    g.dispose();

    myComponent.remove(rComponent);

    return visibleRightTop;
  }

  protected void doPaintTooltipImage(Component rComponent, Rectangle cellBounds, int height, Graphics2D g, KeyType key) {
    myRendererPane.paintComponent(g, rComponent, myComponent, 0, 0, cellBounds.width, height, true);
  }

  protected Rectangle getVisibleRect(KeyType key) {
    return myComponent.getVisibleRect();
  }

  protected abstract Rectangle getCellBounds(KeyType key, Component rendererComponent);

  protected abstract Component getRendererComponent(KeyType key);
}