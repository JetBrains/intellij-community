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
package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class Splitter extends JPanel {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Splitter");
  @NonNls public static final String PROP_PROPORTION = "proportion";

  private int myDividerWidth;
  /**
   * /------/
   * |  1   |
   * This is vertical split |------|
   * |  2   |
   * /------/
   * <p/>
   * /-------/
   * |   |   |
   * This is horizontal split | 1 | 2 |
   * |   |   |
   * /-------/
   */
  private boolean myVerticalSplit;
  private boolean myHonorMinimumSize = false;
  private final float myMinProp;
  private final float myMaxProp;


  private float myProportion;

  private final Divider myDivider;
  private JComponent mySecondComponent;
  private JComponent myFirstComponent;
  private final FocusWatcher myFocusWatcher;
  private boolean myShowDividerIcon;
  private boolean myShowDividerControls;
  private static final Rectangle myNullBounds = new Rectangle();


  /**
   * Creates horizontal split with proportion equals to .5f
   */
  public Splitter() {
    this(false);
  }

  /**
   * Creates split with specified orientation and proportion equals to .5f
   */
  public Splitter(boolean vertical) {
    this(vertical, .5f);
  }

  /**
   * Creates split with specified orientation and proportion.
   */
  public Splitter(boolean vertical, float proportion) {
    this(vertical, proportion, 0.0f, 1.0f);
  }

  public Splitter(boolean vertical, float proportion, float minProp, float maxProp) {
    myMinProp = minProp;
    myMaxProp = maxProp;
    LOG.assertTrue(minProp >= 0.0f);
    LOG.assertTrue(maxProp <= 1.0f);
    LOG.assertTrue(minProp <= maxProp);
    myVerticalSplit = vertical;
    myShowDividerControls = false;
    myShowDividerIcon = true;
    myHonorMinimumSize = true;
    myDivider = createDivider();
    setProportion(proportion);
    myDividerWidth = 7;
    super.add(myDivider);
    myFocusWatcher = new FocusWatcher();
    myFocusWatcher.install(this);
  }

  public void setShowDividerControls(boolean showDividerControls) {
    myShowDividerControls = showDividerControls;
    setOrientation(myVerticalSplit);
  }

  public void setShowDividerIcon(boolean showDividerIcon) {
    myShowDividerIcon = showDividerIcon;
    setOrientation(myVerticalSplit);
  }

  public boolean isShowDividerIcon() {
    return myShowDividerIcon;
  }

  public boolean isShowDividerControls() {
    return myShowDividerControls;
  }

  public boolean isHonorMinimumSize() {
    return myHonorMinimumSize;
  }

  public void setHonorComponentsMinimumSize(boolean honorMinimumSize) {
    myHonorMinimumSize = honorMinimumSize;
  }

  /**
   * This is temporary solution for UIDesigner. <b>DO NOT</b> use it from code.
   *
   * @see #setFirstComponent(JComponent)
   * @see #setSecondComponent(JComponent)
   * @deprecated
   */
  public Component add(Component comp) {
    final int childCount = getComponentCount();
    LOG.assertTrue(childCount >= 1);
    if (childCount > 3) {
      throw new IllegalStateException("" + childCount);
    }
    LOG.assertTrue(childCount <= 3);
    if (childCount == 1) {
      setFirstComponent((JComponent)comp);
    }
    else {
      setSecondComponent((JComponent)comp);
    }
    return comp;
  }

  public void dispose() {
    myFocusWatcher.deinstall(this);
  }

  protected Divider createDivider() {
    return new Divider();
  }

  public boolean isVisible() {
    return super.isVisible() &&
           (myFirstComponent != null && myFirstComponent.isVisible() || mySecondComponent != null && mySecondComponent.isVisible());
  }

  public Dimension getMinimumSize() {
    final int dividerWidth = getDividerWidth();
    if (myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null && mySecondComponent.isVisible()) {
      final Dimension firstMinSize = myFirstComponent.getMinimumSize();
      final Dimension secondMinSize = mySecondComponent.getMinimumSize();
      return getOrientation()
             ? new Dimension(Math.max(firstMinSize.width, secondMinSize.width), firstMinSize.height + dividerWidth + secondMinSize.height)
             : new Dimension(firstMinSize.width + dividerWidth + secondMinSize.width, Math.max(firstMinSize.height, secondMinSize.height));
    }

    if (myFirstComponent != null && myFirstComponent.isVisible()) { // only first component is visible
      return myFirstComponent.getMinimumSize();
    }

    if (mySecondComponent != null && mySecondComponent.isVisible()) { // only second component is visible
      return mySecondComponent.getMinimumSize();
    }

    return super.getMinimumSize();
  }

  @Override
  public Dimension getPreferredSize() {
    final int dividerWidth = getDividerWidth();
    if (myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null && mySecondComponent.isVisible()) {
      final Dimension firstPrefSize = myFirstComponent.getPreferredSize();
      final Dimension secondPrefSize = mySecondComponent.getPreferredSize();
      return getOrientation()
             ? new Dimension(Math.max(firstPrefSize.width, secondPrefSize.width),
                             firstPrefSize.height + dividerWidth + secondPrefSize.height)
             : new Dimension(firstPrefSize.width + dividerWidth + secondPrefSize.width,
                             Math.max(firstPrefSize.height, secondPrefSize.height));
    }

    if (myFirstComponent != null && myFirstComponent.isVisible()) { // only first component is visible
      return myFirstComponent.getPreferredSize();
    }

    if (mySecondComponent != null && mySecondComponent.isVisible()) { // only second component is visible
      return mySecondComponent.getPreferredSize();
    }

    return super.getPreferredSize();
  }

  public void doLayout() {
    final double width = getWidth();
    final double height = getHeight();

    final double componentSize = getOrientation() ? height : width;
    if (componentSize <= 0) return;

    if (!isNull(myFirstComponent) && myFirstComponent.isVisible() && !isNull(mySecondComponent) && mySecondComponent.isVisible()) {
      // both first and second components are visible
      Rectangle firstRect = new Rectangle();
      Rectangle dividerRect = new Rectangle();
      Rectangle secondRect = new Rectangle();

      double dividerWidth = getDividerWidth();
      double firstComponentSize;
      double secondComponentSize;

      if (componentSize <= dividerWidth) {
        firstComponentSize = 0;
        secondComponentSize = 0;
        dividerWidth = componentSize;
      }
      else {
        firstComponentSize = myProportion * (float)(componentSize - dividerWidth);
        secondComponentSize = getOrientation() ? height - firstComponentSize - dividerWidth : width - firstComponentSize - dividerWidth;

        if (isHonorMinimumSize()) {

          final double firstMinSize = getOrientation() ? myFirstComponent.getMinimumSize().getHeight() : myFirstComponent.getMinimumSize().getWidth();
          final double secondMinSize = getOrientation() ? mySecondComponent.getMinimumSize().getHeight() : mySecondComponent.getMinimumSize().getWidth();

          if (firstComponentSize + secondComponentSize < firstMinSize + secondMinSize) {
            double proportion = firstMinSize / (firstMinSize + secondMinSize);
            firstComponentSize = (int)(proportion * (float)(componentSize - dividerWidth));
            secondComponentSize = getOrientation() ? height - firstComponentSize - dividerWidth : width - firstComponentSize - dividerWidth;
          }
          else {
            if (firstComponentSize < firstMinSize) {
              secondComponentSize -= firstMinSize - firstComponentSize;
              firstComponentSize = firstMinSize;
            }
            else if (secondComponentSize < secondMinSize) {
              firstComponentSize -= secondMinSize - secondComponentSize;
              secondComponentSize = secondMinSize;
            }
          }
        }
      }

      myProportion = (float)(firstComponentSize / (firstComponentSize + secondComponentSize));

      if (getOrientation()) {
        firstRect.setBounds(0, 0, (int)width, (int)firstComponentSize);
        dividerRect.setBounds(0, (int)firstComponentSize, (int)width, (int)dividerWidth);
        secondRect.setBounds(0, (int)(firstComponentSize + dividerWidth), (int)width, (int)secondComponentSize);
      }
      else {
        firstRect.setBounds(0, 0, (int)firstComponentSize, (int)height);
        dividerRect.setBounds((int)firstComponentSize, 0, (int)dividerWidth, (int)height);
        secondRect.setBounds((int)(firstComponentSize + dividerWidth), 0, (int)secondComponentSize, (int)height);
      }
      myDivider.setVisible(true);
      myFirstComponent.setBounds(firstRect);
      myDivider.setBounds(dividerRect);
      mySecondComponent.setBounds(secondRect);
      myFirstComponent.revalidate();
      mySecondComponent.revalidate();
    }
    else if (!isNull(myFirstComponent) && myFirstComponent.isVisible()) { // only first component is visible
      hideNull(mySecondComponent);
      myDivider.setVisible(false);
      myFirstComponent.setBounds(0, 0, (int)width, (int)height);
      myFirstComponent.revalidate();
    }
    else if (!isNull(mySecondComponent) && mySecondComponent.isVisible()) { // only second component is visible
      hideNull(myFirstComponent);
      myDivider.setVisible(false);
      mySecondComponent.setBounds(0, 0, (int)width, (int)height);
      mySecondComponent.revalidate();
    }
    else { // both components are null or invisible
      myDivider.setVisible(false);
      if (myFirstComponent != null) {
        myFirstComponent.setBounds(0, 0, 0, 0);
        myFirstComponent.revalidate();
      }
      else {
        hideNull(myFirstComponent);
      }
      if (mySecondComponent != null) {
        mySecondComponent.setBounds(0, 0, 0, 0);
        mySecondComponent.revalidate();
      }
      else {
        hideNull(mySecondComponent);
      }
    }
    myDivider.revalidate();
  }

  static boolean isNull(Component component) {
    return NullableComponent.Check.isNull(component);
  }

  static void hideNull(Component component) {
    if (component instanceof NullableComponent) {
      if (!component.getBounds().equals(myNullBounds)) {
        component.setBounds(myNullBounds);
        component.validate();
      }
    }
  }

  public int getDividerWidth() {
    return myDividerWidth;
  }

  public void setDividerWidth(int width) {
    if (width <= 0) {
      throw new IllegalArgumentException("Wrong divider width: " + width);
    }
    if (myDividerWidth != width) {
      myDividerWidth = width;
      revalidate();
      repaint();
    }
  }

  public float getProportion() {
    return myProportion;
  }

  public void setProportion(float proportion) {
    if (myProportion == proportion) {
      return;
    }
    if (proportion < .0f || proportion > 1.0f) {
      throw new IllegalArgumentException("Wrong proportion: " + proportion);
    }
    if (proportion < myMinProp) proportion = myMinProp;
    if (proportion > myMaxProp) proportion = myMaxProp;
    float oldProportion = myProportion;
    myProportion = proportion;
    firePropertyChange(PROP_PROPORTION, new Float(oldProportion), new Float(myProportion));
    revalidate();
    repaint();
  }

  /**
   * Swaps components.
   */
  public void swapComponents() {
    JComponent tmp = myFirstComponent;
    myFirstComponent = mySecondComponent;
    mySecondComponent = tmp;
    revalidate();
    repaint();
  }

  /**
   * @return <code>true</code> if splitter has vertical orientation, <code>false</code> otherwise
   */
  public boolean getOrientation() {
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit <code>true</code> means that splitter will have vertical split
   */
  public void setOrientation(boolean verticalSplit) {
    myVerticalSplit = verticalSplit;
    myDivider.setOrientation(verticalSplit);
    revalidate();
    repaint();
  }

  public JComponent getFirstComponent() {
    return myFirstComponent;
  }

  /**
   * Sets component which is located as the "first" splitted area. The method doesn't validate and
   * repaint the splitter. If there is already
   *
   * @param component
   */
  public void setFirstComponent(JComponent component) {
    if (myFirstComponent != component) {
      if (myFirstComponent != null) {
        remove(myFirstComponent);
      }
      myFirstComponent = component;
      if (myFirstComponent != null) {
        super.add(myFirstComponent);
      }
      revalidate();
      repaint();
    }
  }

  public JComponent getSecondComponent() {
    return mySecondComponent;
  }

  public JComponent getOtherComponent(final Component comp) {
    if (comp.equals(getFirstComponent())) return getSecondComponent();
    if (comp.equals(getSecondComponent())) return getFirstComponent();
    LOG.error("invalid component");
    return getFirstComponent();
  }

  /**
   * Sets component which is located as the "secont" splitted area. The method doesn't validate and
   * repaint the splitter.
   *
   * @param component
   */
  public void setSecondComponent(JComponent component) {
    if (mySecondComponent != component) {
      if (mySecondComponent != null) {
        remove(mySecondComponent);
      }
      mySecondComponent = component;
      if (mySecondComponent != null) {
        super.add(mySecondComponent);
      }
      revalidate();
      repaint();
    }
  }

  public JPanel getDivider() {
    return myDivider;
  }

  public class Divider extends JPanel {
    private boolean myResizeEnabled;
    protected Point myPoint;

    public Divider() {
      super(new GridBagLayout());
      myResizeEnabled = true;
      setFocusable(false);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);

      setOrientation(myVerticalSplit);
    }

    private void setOrientation(boolean isVerticalSplit) {
      removeAll();

      setCursor(getOrientation() ?
                Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) :
                Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

      if (!myShowDividerControls && !myShowDividerIcon) {
        return;
      }

      Icon glueIcon = IconLoader.getIcon(isVerticalSplit ? "/general/splitGlueV.png" : "/general/splitGlueH.png");
      int leftInsetIcon = 1;
      int leftInsetArrow = 0;
      int glueFill = isVerticalSplit ? GridBagConstraints.VERTICAL : GridBagConstraints.HORIZONTAL;
      add(new JLabel(glueIcon), new GridBagConstraints(0, 0, 1, 1, 0, 0,
                                                       GridBagConstraints.CENTER, GridBagConstraints.EAST,
                                                       new Insets(0, leftInsetIcon, 0, 0), 0, 0));

      if (myShowDividerControls) {
        int xMask = isVerticalSplit ? 1 : 0;
        int yMask = isVerticalSplit ? 0 : 1;

        JLabel splitDownlabel = new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitDown.png" : "/general/splitRight.png"));
        splitDownlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        splitDownlabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.down.tooltip.text") : UIBundle
          .message("splitter.right.tooltip.text"));
        splitDownlabel.addMouseListener(new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            setProportion(1.0f - getMinProportion(mySecondComponent));
          }
        });
        add(splitDownlabel, new GridBagConstraints(isVerticalSplit ? 1 : 0, isVerticalSplit ? 0 : 5, 1, 1, 0, 0, GridBagConstraints.CENTER,
                                                   GridBagConstraints.NONE, new Insets(0, leftInsetArrow, 0, 0), 0, 0));
        //
        add(new JLabel(glueIcon),
            new GridBagConstraints(2 * xMask, 2 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, leftInsetIcon, 0, 0), 0, 0));
        JLabel splitCenterlabel =
          new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitCenterV.png" : "/general/splitCenterH.png"));
        splitCenterlabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        splitCenterlabel.setToolTipText(UIBundle.message("splitter.center.tooltip.text"));
        splitCenterlabel.addMouseListener(new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            setProportion(.5f);
          }
        });
        add(splitCenterlabel, new GridBagConstraints(3 * xMask, 3 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                     new Insets(0, leftInsetArrow, 0, 0), 0, 0));
        add(new JLabel(glueIcon),
            new GridBagConstraints(4 * xMask, 4 * yMask, 1, 1, 0, 0, GridBagConstraints.CENTER, glueFill, new Insets(0, leftInsetIcon, 0, 0), 0, 0));
        //
        JLabel splitUpLabel = new JLabel(IconLoader.getIcon(isVerticalSplit ? "/general/splitUp.png" : "/general/splitLeft.png"));
        splitUpLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        splitUpLabel.setToolTipText(isVerticalSplit ? UIBundle.message("splitter.up.tooltip.text") : UIBundle
          .message("splitter.left.tooltip.text"));
        splitUpLabel.addMouseListener(new MouseAdapter() {
          public void mouseClicked(MouseEvent e) {
            setProportion(getMinProportion(myFirstComponent));
          }
        });
        add(splitUpLabel, new GridBagConstraints(isVerticalSplit ? 5 : 0, isVerticalSplit ? 0 : 1, 1, 1, 0, 0, GridBagConstraints.CENTER,
                                                 GridBagConstraints.NONE, new Insets(0, leftInsetArrow, 0, 0), 0, 0));
        add(new JLabel(glueIcon), new GridBagConstraints(6 * xMask, 6 * yMask, 1, 1, 0, 0,
                                                         isVerticalSplit ? GridBagConstraints.WEST : GridBagConstraints.SOUTH, glueFill,
                                                         new Insets(0, leftInsetIcon, 0, 0), 0, 0));
      }
      
      revalidate();
      repaint();
    }

    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (!myResizeEnabled) return;
      if (MouseEvent.MOUSE_DRAGGED == e.getID()) {
        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), Splitter.this);
        float proportion;
        if (getOrientation()) {
          if (getHeight() > 0) {
            proportion = Math.min(1.0f, Math.max(.0f, Math.min(Math.max(getMinProportion(myFirstComponent), (float)myPoint.y / (float)Splitter.this.getHeight()), 1 - getMinProportion(mySecondComponent))));
            setProportion(proportion);
          }
        }
        else {
          if (getWidth() > 0) {
            proportion = Math.min(1.0f, Math.max(.0f, Math.min(Math.max(getMinProportion(myFirstComponent), (float)myPoint.x / (float)Splitter.this.getWidth()), 1 - getMinProportion(mySecondComponent))));
            setProportion(proportion);
          }
        }
      }
    }

    private float getMinProportion(JComponent component) {
      if (isHonorMinimumSize()) {
        if (component != null && myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null &&
            mySecondComponent.isVisible()) {
          if (getOrientation()) {
            return (float)component.getMinimumSize().height / (float)(Splitter.this.getHeight() - getDividerWidth());
          }
          else {
            return (float)component.getMinimumSize().width / (float)(Splitter.this.getWidth() - getDividerWidth());
          }
        }
      }
      return 0.0f;
    }

    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (!myResizeEnabled) return;
      switch (e.getID()) {
        case MouseEvent.MOUSE_CLICKED: {
          if (e.getClickCount() == 2) {
            Splitter.this.setProportion(.5f);
          }
          break;
        }
      }
    }

    public void setResizeEnabled(boolean resizeEnabled) {
      myResizeEnabled = resizeEnabled;
    }
  }
}
