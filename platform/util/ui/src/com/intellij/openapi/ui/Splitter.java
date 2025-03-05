// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBSwingUtilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static java.lang.Math.abs;

public class Splitter extends JPanel implements Splittable {
  private static final Logger LOG = Logger.getInstance(Splitter.class);
  public static final @NonNls String PROP_PROPORTION = "proportion";
  public static final @NonNls String PROP_ORIENTATION = "orientation";

  private int myDividerWidth;
  /**
   * If {@code true}, the components are displayed above one another.<br>
   * If {@code false}, the components are displayed side by side.
   */
  private boolean myVerticalSplit;
  private boolean myHonorMinimumSize;
  private boolean myHonorPreferredSize;
  private boolean myUseViewportViewSizes;
  private final float myMinProp;
  private final float myMaxProp;


  @ApiStatus.Internal
  protected float myProportion;// first size divided by (first + second)
  private Float myLagProportion;

  protected final Divider myDivider;
  private JComponent mySecondComponent;
  private JComponent myFirstComponent;
  private boolean myShowDividerIcon;
  private boolean myShowDividerControls;
  private boolean mySkipNextLayout;
  private static final Rectangle myNullBounds = new Rectangle();

  public enum LackOfSpaceStrategy {
    SIMPLE_RATIO, //default
    HONOR_THE_FIRST_MIN_SIZE,
    HONOR_THE_SECOND_MIN_SIZE
  }
  private @NotNull LackOfSpaceStrategy myLackOfSpaceStrategy = LackOfSpaceStrategy.SIMPLE_RATIO;

  public enum DividerPositionStrategy {
    KEEP_PROPORTION, //default
    KEEP_FIRST_SIZE,
    KEEP_SECOND_SIZE,
    DISTRIBUTE
  }
  private @NotNull DividerPositionStrategy myDividerPositionStrategy = DividerPositionStrategy.KEEP_PROPORTION;


  /**
   * Creates a horizontal split (with components that are side by side),
   * both components get the same proportion of the available space.
   */
  public Splitter() {
    this(false);
  }

  /**
   * Creates a split with the specified orientation,
   * both components get the same proportion of the available space.
   * The proportion may later be changed arbitrarily between 0.0 and 1.0.
   *
   * @param vertical If {@code true}, the components are displayed above one another.
   *                 If {@code false}, the components are displayed side by side.
   */
  public Splitter(boolean vertical) {
    this(vertical, 0.5f);
  }

  /**
   * Creates a split with the specified orientation and initial proportion.
   * The proportion may later be changed arbitrarily between 0.0 and 1.0.
   *
   * @param vertical If {@code true}, the components are displayed above one another.
   *                 If {@code false}, the components are displayed side by side.
   * @param proportion The initial proportion of the splitter, between 0.0 and 1.0.
   */
  public Splitter(boolean vertical, float proportion) {
    this(vertical, proportion, 0.0f, 1.0f);
  }

  /**
   * Creates a split with the specified orientation and proportion.
   *
   * @param vertical If {@code true}, the components are displayed above one another.
   *                 If {@code false}, the components are displayed side by side.
   * @param proportion The initial proportion of the splitter, between 0.0 and 1.0.
   */
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
    myHonorPreferredSize = false;
    myDivider = createDivider();
    setProportion(proportion);
    myDividerWidth = 7;
    super.add(myDivider);
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

  public boolean isHonorPreferredSize() {
    return myHonorPreferredSize;
  }

  public boolean isUseViewportViewSizes() {
    return myUseViewportViewSizes;
  }

  public void setUseViewportViewSizes(boolean useViewportViewSizes) {
    myUseViewportViewSizes = useViewportViewSizes;
  }

  public void setHonorComponentsPreferredSize(boolean honorPreferredSize) {
    myHonorPreferredSize = honorPreferredSize;
  }

  public void setLackOfSpaceStrategy(@NotNull LackOfSpaceStrategy strategy) {
    myLackOfSpaceStrategy = strategy;
  }
  public @NotNull LackOfSpaceStrategy getLackOfSpaceStrategy() {
    return myLackOfSpaceStrategy;
  }
  public void setDividerPositionStrategy(@NotNull DividerPositionStrategy dividerPositionStrategy) {
    myDividerPositionStrategy = dividerPositionStrategy;
  }

  public @NotNull DividerPositionStrategy getDividerPositionStrategy() {
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
      throw new IllegalStateException(String.valueOf(childCount));
    }
    if (childCount == 1) {
      setFirstComponent((JComponent)comp);
    }
    else {
      setSecondComponent((JComponent)comp);
    }
    return comp;
  }

  public void dispose() {
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
    if (isMinimumSizeSet()) return super.getMinimumSize(); // do not violate Swing's contract

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
    if (isPreferredSizeSet()) return super.getPreferredSize(); // do not violate Swing's contract

    final int dividerWidth = getDividerWidth();
    if (myFirstComponent != null && myFirstComponent.isVisible() && mySecondComponent != null && mySecondComponent.isVisible()) {
      final Dimension firstPrefSize = unwrap(myFirstComponent).getPreferredSize();
      final Dimension secondPrefSize = unwrap(mySecondComponent).getPreferredSize();
      if (myDividerPositionStrategy == DividerPositionStrategy.DISTRIBUTE) {
        Dimension firstMinSize = unwrap(myFirstComponent).getMinimumSize();
        Dimension secondMinSize = unwrap(mySecondComponent).getMinimumSize();
        firstPrefSize.width = Math.max(firstMinSize.width, firstPrefSize.width);
        firstPrefSize.height = Math.max(firstMinSize.height, firstPrefSize.height);
        secondPrefSize.width = Math.max(secondMinSize.width, secondPrefSize.width);
        secondPrefSize.height = Math.max(secondMinSize.height, secondPrefSize.height);
      }
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
    int total = myVerticalSplit ? h : w;
    if (w > 0 && h > 0 && myDividerPositionStrategy != DividerPositionStrategy.KEEP_PROPORTION
        && isComponentVisible(myFirstComponent) && isComponentVisible(mySecondComponent)
        && total > 2 * getDividerWidth() && total != getDimension(getSize())) {
      if (myDividerPositionStrategy == DividerPositionStrategy.KEEP_FIRST_SIZE) {
        myProportion = getProportionForFirstSize((int)getDimension(myFirstComponent.getSize()), total);
      }
      else if (myDividerPositionStrategy == DividerPositionStrategy.KEEP_SECOND_SIZE) {
        myProportion = getProportionForSecondSize((int)getDimension(mySecondComponent.getSize()), total);
      }
      else if (myDividerPositionStrategy == DividerPositionStrategy.DISTRIBUTE) {
        if (myLagProportion == null) myLagProportion = myProportion;
        Component first = unwrap(myFirstComponent);
        Component second = unwrap(mySecondComponent);
        myProportion = getDistributeSizeChange(
          (int)getDimension(myFirstComponent.getSize()),
          (int)getDimension(first.getMinimumSize()),
          (int)getDimension(first.getPreferredSize()),
          (int)getDimension(first.getMaximumSize()),
          (int)getDimension(mySecondComponent.getSize()),
          (int)getDimension(second.getMinimumSize()),
          (int)getDimension(second.getPreferredSize()),
          (int)getDimension(second.getMaximumSize()),
          total - getDividerWidth(),
          myLagProportion,
          myLackOfSpaceStrategy == LackOfSpaceStrategy.SIMPLE_RATIO ? null :
          myLackOfSpaceStrategy == LackOfSpaceStrategy.HONOR_THE_SECOND_MIN_SIZE);
      }
    }
    super.reshape(x, y, w, h);
  }

  private static boolean isComponentVisible(JComponent component) {
    return !isNull(component) && component.isVisible() && !component.getBounds().isEmpty();
  }

  private static float getDistributeSizeChange(int size1, int mSize1, int pSize1, int mxSize1,
                                               int size2, int mSize2, int pSize2, int mxSize2,
                                               int totalSize, float oldProportion,
                                               @Nullable Boolean stretchFirst) {
    //clamp
    mSize1 = Math.min(mSize1, mxSize1);
    mSize2 = Math.min(mSize2, mxSize2);
    pSize1 = MathUtil.clamp(pSize1, mSize1, mxSize1);
    pSize2 = MathUtil.clamp(pSize2, mSize2, mxSize2);
    int delta = totalSize - (size1 + size2);

    int[] size = {size1, size2};
    if (delta >= 0) {
      delta = stretchTo(size, mSize1, mSize2, delta, oldProportion);
      delta = stretchTo(size, pSize1, pSize2, delta, oldProportion);
      delta = stretchTo(size, mxSize1, mxSize2, delta, oldProportion);
    }
    else {
      delta = stretchTo(size, mxSize1, mxSize2, delta, oldProportion);
      delta = stretchTo(size, pSize1, pSize2, delta, oldProportion);
      delta = stretchTo(size, mSize1, mSize2, delta, oldProportion);
    }
    if (delta != 0) {
      if (stretchFirst == null) {
        int p0 = computePortion(size, delta, oldProportion);
        size[0] += p0;
        size[1] += delta - p0;
      }
      else {
        size[stretchFirst ? 0 : 1] += delta;
      }
    }
    return (float)size[0] / totalSize;
  }

  private static int stretchTo(int[] size, int tgt0, int tgt1, int delta, double oldProportion) {
    int d0 = tgt0 - size[0];
    if ((d0 >= 0) != (delta >= 0)) d0 = 0;
    int d1 = tgt1 - size[1];
    if ((d1 >= 0) != (delta >= 0)) d1 = 0;
    if (abs(d0 + d1) > abs(delta)) {
      int p0 = computePortion(size, delta, oldProportion);
      int p1 = delta - p0;
      if (abs(p0) > abs(d0)) {
        p0 = d0;
        p1 = delta - d0;
      }
      else if (abs(p1) > abs(d1)) {
        p0 = delta - d1;
        p1 = d1;
      }
      size[0] += p0;
      size[1] += p1;
      return 0;
    }
    else {
      size[0] += d0;
      size[1] += d1;
      return delta - (d0 + d1);
    }
  }

  private static int computePortion(int[] size, int delta, double oldProportion) {
    int offset = (int)Math.round((size[0] + size[1] + delta) * oldProportion - size[0]);
    if ((offset < 0) != (delta < 0)) return 0;
    return abs(offset) > abs(delta) ? delta : offset;
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
    var insets = getInsets();
    int width = getWidth() - insets.left - insets.right;
    int height = getHeight() - insets.top - insets.bottom;

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
        size1 = computeFirstComponentSize(total - d);
      }

      int iSize1 = Math.max(0, (int)Math.round(size1));
      int iSize2 = Math.max(0, total - iSize1 - d);

      if (isVertical()) {
        firstRect.setBounds(insets.left, insets.top, width, iSize1);
        dividerRect.setBounds(insets.left, insets.top + iSize1, width, d);
        secondRect.setBounds(insets.left, insets.top + iSize1 + d, width, iSize2);
      }
      else {
        firstRect.setBounds(insets.left, insets.top, iSize1, height);
        dividerRect.setBounds(insets.left + iSize1, insets.top, d, height);
        secondRect.setBounds((insets.left + iSize1 + d), insets.top, iSize2, height);
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
      myFirstComponent.setBounds(insets.left, insets.top, width, height);
      //myFirstComponent.revalidate();
    }
    else if (!isNull(mySecondComponent) && mySecondComponent.isVisible()) { // only second component is visible
      hideNull(myFirstComponent);
      myDivider.setVisible(false);
      mySecondComponent.setBounds(insets.left, insets.top, width, height);
      //mySecondComponent.revalidate();
    }
    else { // both components are null or invisible
      myDivider.setVisible(false);
      if (myFirstComponent != null) {
        myFirstComponent.setBounds(insets.left, insets.top, 0, 0);
        //myFirstComponent.revalidate();
      }
      else {
        hideNull(myFirstComponent);
      }
      if (mySecondComponent != null) {
        mySecondComponent.setBounds(insets.left, insets.top, 0, 0);
        //mySecondComponent.revalidate();
      }
      else {
        hideNull(mySecondComponent);
      }
    }
    //myDivider.revalidate();
  }

  private double computeFirstComponentSize(int total) {
    double size1 = myProportion * total;
    double size2 = total - size1;

    if (!isHonorMinimumSize()) {
      return size1;
    }
    double mSize1 = getDimension(myFirstComponent.getMinimumSize());
    double mSize2 = getDimension(mySecondComponent.getMinimumSize());
    if (myHonorPreferredSize && size1 > mSize1 && size2 > mSize2) {
      mSize1 = getDimension(myFirstComponent.getPreferredSize());
      mSize2 = getDimension(mySecondComponent.getPreferredSize());
    }

    if (total < mSize1 + mSize2) {
      size1 = switch (myLackOfSpaceStrategy) {
        case SIMPLE_RATIO -> {
          double proportion = mSize1 / (mSize1 + mSize2);
          yield proportion * total;
        }
        case HONOR_THE_FIRST_MIN_SIZE -> mSize1;
        case HONOR_THE_SECOND_MIN_SIZE -> total - mSize2;
      };
    }
    else {
      if (size1 < mSize1) {
        size1 = mSize1;
      }
      else if (size2 < mSize2) {
        size2 = mSize2;
        size1 = total - size2;
      }
    }
    return size1;
  }

  private double getDimension(Dimension size) {
    return isVertical() ? size.getHeight() : size.getWidth();
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

  public float getMinimumProportion() {
    return myMinProp;
  }

  public float getMaximumProportion() {
    return myMaxProp;
  }

  public void setDefaultProportion() {
    Component first = unwrap(myFirstComponent);
    Component second = unwrap(mySecondComponent);
    if (first != null && second != null) {
      double p1 = Math.max(getDimension(first.getPreferredSize()), getDimension(first.getMinimumSize()));
      double p2 = Math.max(getDimension(second.getPreferredSize()), getDimension(second.getMinimumSize()));
      setProportion((float)(p1 / (p1 + p2)));
    }
  }

  @Override
  public void setProportion(float proportion) {
    myLagProportion = null;
    if (myProportion == proportion) {
      return;
    }
    if (proportion < .0f || proportion > 1.0f) {
      LOG.warn("Wrong proportion: " + proportion);
    }
    proportion = MathUtil.clamp(proportion, myMinProp, myMaxProp);
    float oldProportion = myProportion;
    myProportion = proportion;
    firePropertyChange(PROP_PROPORTION, Float.valueOf(oldProportion), Float.valueOf(myProportion));
    revalidate();
    repaint();
  }

  public void swapComponents() {
    JComponent tmp = myFirstComponent;
    myFirstComponent = mySecondComponent;
    mySecondComponent = tmp;
    revalidate();
    repaint();
  }

  /**
   * @return {@code true} if the components are displayed above one another,
   * {@code false} if the components are displayed side by side
   */
  @Override
  public boolean getOrientation() {
    return myVerticalSplit;
  }

  /**
   * @return {@code true} if the components are displayed above one another,
   * {@code false} if the components are displayed side by side
   */
  public boolean isVertical() {
    return myVerticalSplit;
  }

  /**
   * @param verticalSplit if {@code true}, the components are displayed above one another;
   *                      if {@code false}, the components are displayed side by side.
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
   * Sets the component which is located as the "first" split area.
   * If the given component is the current one, the method neither validates nor repaints the splitter.
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
   * Sets the component which is located as the "second" split area.
   * If the given component is the current one, the method neither validates nor repaints the splitter.
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
    Component component = first ? myFirstComponent : mySecondComponent;
    Component other = first ? mySecondComponent : myFirstComponent;
    if (isHonorMinimumSize()) {
      boolean bothVisible = component != null && component.isVisible()
                            && other != null && other.isVisible();
      if (bothVisible) {
        double size = getDimension(component.getSize());
        component = unwrap(component);
        other = unwrap(other);
        double min = getDimension(component.getMinimumSize());
        double oMax = getDimension(other.getMaximumSize());
        double total = getDimension(getSize()) - getDividerWidth();
        double oMaxP = (total - oMax) / total;
        double minP = size < min ? 0 : min / total;
        return (float)Math.max(oMaxP, minP);
      }
    }
    return 0.0f;
  }

  private Component unwrap(Component c) {
    if (!myUseViewportViewSizes) return c;
    JScrollPane scrollPane = ObjectUtils.tryCast(c, JScrollPane.class);
    JViewport viewport = scrollPane == null ? null : scrollPane.getViewport();
    Component view = viewport == null ? null : viewport.getView();
    return view == null ? c : view;
  }

  @Override
  public @NotNull Component asComponent() {
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
      enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
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
            float min = getMinProportion(true);
            float max = 1 - getMinProportion(false);
            proportion = MathUtil.clamp(Math.min(max, Math.max((float)myPoint.y / (float)Splitter.this.getHeight(), min)), .0f, 1.0f);
            setProportion(proportion);
          }
        }
        else {
          if (getWidth() > 0) {
            float min = getMinProportion(true);
            float max = 1 - getMinProportion(false);
            proportion = MathUtil.clamp(Math.min(max, Math.max((float)myPoint.x / (float)Splitter.this.getWidth(), min)), .0f, 1.0f);
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

    @Override
    protected Graphics getComponentGraphics(Graphics g) {
      return JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g));
    }
  }
}
