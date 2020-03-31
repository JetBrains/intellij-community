// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.FocusWatcher;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Vladimir Kondratyev
 */
public class Splitter extends JPanel implements Splittable {
  private static final Icon SplitGlueH = EmptyIcon.create(6, 17);
  private static final Icon SplitGlueV = EmptyIcon.create(17, 6);
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Splitter");
  @NonNls public static final String PROP_PROPORTION = "proportion";
  @NonNls public static final String PROP_ORIENTATION = "orientation";

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
  private boolean myHonorMinimumSize;
  private final float myMinProp;
  private final float myMaxProp;


  protected float myProportion;// first size divided by (first + second)

  protected final Divider myDivider;
  private JComponent mySecondComponent;
  private JComponent myFirstComponent;
  private final FocusWatcher myFocusWatcher;
  private boolean myShowDividerIcon;
  private boolean myShowDividerControls;
  private boolean mySkipNextLayout;
  private static final Rectangle myNullBounds = new Rectangle();

  public enum LackOfSpaceStrategy {
    SIMPLE_RATIO, //default
    HONOR_THE_FIRST_MIN_SIZE,
    HONOR_THE_SECOND_MIN_SIZE
  }
  @NotNull
  private LackOfSpaceStrategy myLackOfSpaceStrategy = LackOfSpaceStrategy.SIMPLE_RATIO;

  public enum DividerPositionStrategy {
    KEEP_PROPORTION, //default
    KEEP_FIRST_SIZE,
    KEEP_SECOND_SIZE
  }
  @NotNull
  private DividerPositionStrategy myDividerPositionStrategy = DividerPositionStrategy.KEEP_PROPORTION;


  /**
   * Creates horizontal split (with components which are side by side) with proportion equals to .5f
   */
  public Splitter() {
    this(false);
  }

  /**
   * Creates split with specified orientation and proportion equals to .5f
   *
   * @param vertical If true, components are displayed above one another. If false, components are displayed side by side.
   */
  public Splitter(boolean vertical) {
    this(vertical, .5f);
  }

  /**
   * Creates split with specified orientation and proportion.
   *
   * @param vertical If true, components are displayed above one another. If false, components are displayed side by side.
   * @param proportion The initial proportion of the splitter (between 0.0f and 1.0f).
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
    setOpaque(false);
  }

  public void setShowDividerControls(boolean showDividerControls) {
    myShowDividerControls = showDividerControls;
    setOrientation(myVerticalSplit);
  }

  public void setShowDividerIcon(boolean showDividerIcon) {
    myShowDividerIcon = showDividerIcon;
    setOrientation(myVerticalSplit);
  }

  public void setResizeEnabled(final boolean value) {
    myDivider.setResizeEnabled(value);
  }

  public void setAllowSwitchOrientationByMouseClick(boolean enabled) {
    myDivider.setSwitchOrientationEnabled(enabled);
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

  public void setLackOfSpaceStrategy(@NotNull LackOfSpaceStrategy strategy) {
    myLackOfSpaceStrategy = strategy;
  }
  @NotNull
  public LackOfSpaceStrategy getLackOfSpaceStrategy() {
    return myLackOfSpaceStrategy;
  }
  public void setDividerPositionStrategy(@NotNull DividerPositionStrategy dividerPositionStrategy) {
    myDividerPositionStrategy = dividerPositionStrategy;
  }

  @NotNull
  public DividerPositionStrategy getDividerPositionStrategy() {
    return myDividerPositionStrategy;
  }

  /**
   * This is temporary solution for UIDesigner. <b>DO NOT</b> use it from code.
   *
   * @deprecated use {@link #setFirstComponent(JComponent)} and {@link #setSecondComponent(JComponent)}
   */
  @Deprecated
  @Override
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
    return new DividerImpl();
  }

  @Override
  public boolean isVisible() {
    return super.isVisible() &&
           (myFirstComponent != null && myFirstComponent.isVisible() || mySecondComponent != null && mySecondComponent.isVisible());
  }

  @Override
  public Dimension getMinimumSize() {
    final int dividerWidth = getDividerWidth();
    if (myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null && mySecondComponent.isVisible()) {
      final Dimension firstMinSize = myFirstComponent.getMinimumSize();
      final Dimension secondMinSize = mySecondComponent.getMinimumSize();
      return isVertical()
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
      return isVertical()
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
  public void skipNextLayout() {
    mySkipNextLayout = true;
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    if (myDividerPositionStrategy != DividerPositionStrategy.KEEP_PROPORTION
        && !isNull(myFirstComponent) && myFirstComponent.isVisible()
        && !isNull(mySecondComponent) && mySecondComponent.isVisible()
        && ((myVerticalSplit && h > 2 * getDividerWidth()) || (!myVerticalSplit && w > 2 * getDividerWidth()))
      && ((myVerticalSplit && h != getHeight()) || (!myVerticalSplit && w != getWidth()))) {
      int total = myVerticalSplit ? h : w;
      if (myDividerPositionStrategy == DividerPositionStrategy.KEEP_FIRST_SIZE) {
        myProportion = getProportionForFirstSize(myVerticalSplit ? myFirstComponent.getHeight() : myFirstComponent.getWidth(), total);
      }
      else if (myDividerPositionStrategy == DividerPositionStrategy.KEEP_SECOND_SIZE) {
        myProportion = getProportionForSecondSize(myVerticalSplit ? mySecondComponent.getHeight() : mySecondComponent.getWidth(), total);
      }
    }
    super.reshape(x, y, w, h);
  }

  @ApiStatus.Internal
  protected final float getProportionForFirstSize(int firstSize, int totalSize) {
    checkSize(firstSize);
    checkTotalSize(totalSize);
    return (float)firstSize / (totalSize - getDividerWidth());
  }

  @ApiStatus.Internal
  protected final float getProportionForSecondSize(int secondSize, int totalSize) {
    checkSize(secondSize);
    checkTotalSize(totalSize);
    return (float)(totalSize - getDividerWidth() - secondSize) / (totalSize - getDividerWidth());
  }

  private static void checkSize(int size) {
    if (size < 0) throw new IllegalArgumentException("size is negative: " + size);
  }

  private void checkTotalSize(int totalSize) {
    int d = getDividerWidth();
    if (totalSize <= d) throw new IllegalArgumentException("divider width >= total size: " + d + " >= " + totalSize);
  }

  @Override
  public void doLayout() {
    if (mySkipNextLayout) {
      mySkipNextLayout = false;
      return;
    }
    int width = getWidth();
    int height = getHeight();

    int total = isVertical() ? height : width;
    if (total <= 0) return;

    if (!isNull(myFirstComponent) && myFirstComponent.isVisible() && !isNull(mySecondComponent) && mySecondComponent.isVisible()) {
      // both first and second components are visible
      Rectangle firstRect = new Rectangle();
      Rectangle dividerRect = new Rectangle();
      Rectangle secondRect = new Rectangle();

      int d = getDividerWidth();
      double size1;

      if (total <= d) {
        size1 = 0;
        d = total;
      }
      else {
        size1 = myProportion * (total - d);
        double size2 = total - size1 - d;

        if (isHonorMinimumSize()) {

          double mSize1 = isVertical() ? myFirstComponent.getMinimumSize().getHeight() : myFirstComponent.getMinimumSize().getWidth();
          double mSize2 = isVertical() ? mySecondComponent.getMinimumSize().getHeight() : mySecondComponent.getMinimumSize().getWidth();

          if (size1 + size2 < mSize1 + mSize2) {
            switch (myLackOfSpaceStrategy) {
              case SIMPLE_RATIO:
                double proportion = mSize1 / (mSize1 + mSize2);
                size1 = proportion * total;
                break;
              case HONOR_THE_FIRST_MIN_SIZE:
                size1 = mSize1;
                break;
              case HONOR_THE_SECOND_MIN_SIZE:
                size1 = total - mSize2 - d;
                break;
            }
          }
          else {
            if (size1 < mSize1) {
              size1 = mSize1;
            }
            else if (size2 < mSize2) {
              size2 = mSize2;
              size1 = total - size2 - d;
            }
          }
        }
      }

      int iSize1 = (int)Math.round(size1);
      int iSize2 = total - iSize1 - d;

      if (isVertical()) {
        firstRect.setBounds(0, 0, width, iSize1);
        dividerRect.setBounds(0, iSize1, width, d);
        secondRect.setBounds(0, iSize1 + d, width, iSize2);
      }
      else {
        firstRect.setBounds(0, 0, iSize1, height);
        dividerRect.setBounds(iSize1, 0, d, height);
        secondRect.setBounds((iSize1 + d), 0, iSize2, height);
      }
      myDivider.setVisible(true);
      myFirstComponent.setBounds(firstRect);
      myDivider.setBounds(dividerRect);
      mySecondComponent.setBounds(secondRect);
      //myFirstComponent.revalidate();
      //mySecondComponent.revalidate();
    }
    else if (!isNull(myFirstComponent) && myFirstComponent.isVisible()) { // only first component is visible
      hideNull(mySecondComponent);
      myDivider.setVisible(false);
      myFirstComponent.setBounds(0, 0, width, height);
      //myFirstComponent.revalidate();
    }
    else if (!isNull(mySecondComponent) && mySecondComponent.isVisible()) { // only second component is visible
      hideNull(myFirstComponent);
      myDivider.setVisible(false);
      mySecondComponent.setBounds(0, 0, width, height);
      //mySecondComponent.revalidate();
    }
    else { // both components are null or invisible
      myDivider.setVisible(false);
      if (myFirstComponent != null) {
        myFirstComponent.setBounds(0, 0, 0, 0);
        //myFirstComponent.revalidate();
      }
      else {
        hideNull(myFirstComponent);
      }
      if (mySecondComponent != null) {
        mySecondComponent.setBounds(0, 0, 0, 0);
        //mySecondComponent.revalidate();
      }
      else {
        hideNull(mySecondComponent);
      }
    }
    //myDivider.revalidate();
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

  @Override
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
   * @return {@code true} if splitter has vertical orientation, {@code false} otherwise
   */
  @Override
  public boolean getOrientation() {
    return myVerticalSplit;
  }

  /**
   * @return true if |-|
   */
  public boolean isVertical() {
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit {@code true} means that splitter will have vertical split
   */
  @Override
  public void setOrientation(boolean verticalSplit) {
    boolean changed = myVerticalSplit != verticalSplit;
    myVerticalSplit = verticalSplit;
    myDivider.setOrientation(verticalSplit);
    if (changed) firePropertyChange(PROP_ORIENTATION, !myVerticalSplit, myVerticalSplit);
    revalidate();
    repaint();
  }

  public JComponent getFirstComponent() {
    return myFirstComponent;
  }

  /**
   * Sets component which is located as the "first" split area. The method doesn't validate and
   * repaint the splitter if there is one already.
   */
  public void setFirstComponent(@Nullable JComponent component) {
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
   * Sets component which is located as the "second" split area. The method doesn't validate and
   * repaint the splitter.
   */
  public void setSecondComponent(@Nullable JComponent component) {
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

  @Override
  public float getMinProportion(boolean first) {
    JComponent component = first? myFirstComponent : mySecondComponent;
    if (isHonorMinimumSize()) {
      if (component != null && myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null &&
          mySecondComponent.isVisible()) {
        if (isVertical()) {
          return (float)component.getMinimumSize().height / (float)(getHeight() - getDividerWidth());
        }
        else {
          return (float)component.getMinimumSize().width / (float)(getWidth() - getDividerWidth());
        }
      }
    }
    return 0.0f;
  }

  @NotNull
  @Override
  public Component asComponent() {
    return this;
  }

  @Override
  public void setDragging(boolean dragging) {
    //ignore
  }

  public JPanel getDivider() {
    return myDivider;
  }

  public class DividerImpl extends Divider {
    private boolean myResizeEnabled;
    private boolean mySwitchOrientationEnabled;
    protected Point myPoint;

    public DividerImpl() {
      super(new GridBagLayout());
      myResizeEnabled = true;
      mySwitchOrientationEnabled = false;
      setFocusable(false);
      enableEvents(MouseEvent.MOUSE_EVENT_MASK | MouseEvent.MOUSE_MOTION_EVENT_MASK);
      //setOpaque(false);
      setOrientation(myVerticalSplit);
    }

    @Override
    public void setOrientation(boolean isVerticalSplit) {
      removeAll();

      setCursor(isVertical() ?
                Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) :
                Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));

      if (!myShowDividerControls && !myShowDividerIcon) {
        return;
      }

      Icon glueIcon = isVerticalSplit ? SplitGlueV : SplitGlueH;
      add(new JLabel(glueIcon), new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
                                                       JBUI.emptyInsets(), 0, 0));

      revalidate();
      repaint();
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
      super.processMouseMotionEvent(e);
      if (!myResizeEnabled) return;
      if (MouseEvent.MOUSE_DRAGGED == e.getID()) {
        myPoint = SwingUtilities.convertPoint(this, e.getPoint(), Splitter.this);
        float proportion;
        if (isVertical()) {
          if (getHeight() > 0) {
            proportion = Math.min(1.0f, Math.max(.0f, Math
              .min(Math.max(getMinProportion(true), (float)myPoint.y / (float)Splitter.this.getHeight()),
                   1 - getMinProportion(false))));
            setProportion(proportion);
          }
        }
        else {
          if (getWidth() > 0) {
            proportion = Math.min(1.0f, Math.max(.0f, Math
              .min(Math.max(getMinProportion(true), (float)myPoint.x / (float)Splitter.this.getWidth()),
                   1 - getMinProportion(false))));
            setProportion(proportion);
          }
        }
      }
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
      super.processMouseEvent(e);
      if (e.getID() == MouseEvent.MOUSE_CLICKED) {
        if (mySwitchOrientationEnabled
            && e.getClickCount() == 1
            && SwingUtilities.isLeftMouseButton(e) && (SystemInfo.isMac ? e.isMetaDown() : e.isControlDown())) {
          Splitter.this.setOrientation(!Splitter.this.getOrientation());
        }
        if (myResizeEnabled && e.getClickCount() == 2) {
          Splitter.this.setProportion(.5f);
        }
      }
    }

    @Override
    public void setResizeEnabled(boolean resizeEnabled) {
      myResizeEnabled = resizeEnabled;
      if (!myResizeEnabled) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
      else {
        setCursor(isVertical() ?
                  Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR) :
                  Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      }
    }

    @Override
    public void setSwitchOrientationEnabled(boolean switchOrientationEnabled) {
      mySwitchOrientationEnabled = switchOrientationEnabled;
    }
  }
}
