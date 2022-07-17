// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Usage:
 * <pre>
 * {@code
 *
 * // First, configure default for every or a specific column:
 * GridBag bag = new GridBag()
 *     .setDefaultAnchor(0, GridBagConstraints.EAST)
 *     .setDefaultAnchor(1, GridBagConstraints.WEST)
 *     .setDefaultWeightX(1, 1)
 *     .setDefaultFill(GridBagConstraints.HORIZONTAL);
 *
 * // Then, add components to a panel:
 *
 * // The following code adds a new line with 2 components with default settings:
 * panel.add(c1, bag.nextLine().next())
 * panel.add(c1, bag.next())
 *
 * // The following code adds a component on the next line that covers all remaining columns:
 * panel.add(c1, bag.nextLine().coverLine())
 *
 * // The following code adds a component on the next line with overridden settings:
 * panel.add(c1, bag.nextLine().next().insets(...).weightx(...))
 *
 * // You also can pre-configure the object and pass it as a constraint:
 * bag.nextLine().next();
 * panel.add(c1, bag)
 * }
 * </pre>
 * Note that every call of {@link #nextLine()} or {@link #next()} resets settings to the defaults for the corresponding column.
 */
public final class GridBag extends GridBagConstraints {
  private int myDefaultAnchor = anchor;
  @NotNull private final Int2IntMap myDefaultColumnAnchors = new Int2IntOpenHashMap();

  private int myDefaultFill = fill;
  @NotNull private final Int2IntMap myDefaultColumnFills = new Int2IntOpenHashMap();

  private double myDefaultWeightX = weightx;
  @NotNull private final Int2DoubleMap myDefaultColumnWeightsX = new Int2DoubleOpenHashMap();
  private double myDefaultWeightY = weighty;
  @NotNull private final Int2DoubleMap myDefaultColumnWeightsY = new Int2DoubleOpenHashMap();

  private int myDefaultPaddingX = ipadx;
  @NotNull private final Int2IntMap myDefaultColumnPaddingsX = new Int2IntOpenHashMap();
  private int myDefaultPaddingY = ipady;
  @NotNull private final Int2IntMap myDefaultColumnPaddingsY = new Int2IntOpenHashMap();

  @Nullable private Insets myDefaultInsets = insets;
  @NotNull private final Int2ObjectMap<Insets> myDefaultColumnInsets = new Int2ObjectOpenHashMap<>();

  public GridBag() {
    gridx = gridy = -1;
  }

  @NotNull
  public GridBag nextLine() {
    gridy++;
    gridx = -1;
    return reset();
  }

  @NotNull
  public GridBag next() {
    gridx++;
    return reset();
  }

  public int getLine() {
    return gridy;
  }

  @NotNull
  public GridBag setLine(int line) {
    gridy = line;
    return this;
  }

  public int getColumn() {
    return gridx;
  }

  @NotNull
  public GridBag setColumn(int cell) {
    gridx = cell;
    return this;
  }

  @NotNull
  public GridBag reset() {
    gridwidth = gridheight = 1;

    int column = gridx;

    anchor(getDefaultAnchor(column));
    fill = getDefaultFill(column);
    weightx(getDefaultWeightX(column));
    weighty(getDefaultWeightY(column));
    padx(getDefaultPaddingX(column));
    pady(getDefaultPaddingY(column));
    insets(getDefaultInsets(column));
    return this;
  }

  @NotNull
  public GridBag anchor(int anchor) {
    this.anchor = anchor;
    return this;
  }

  @NotNull
  public GridBag fillCell() {
    fill = GridBagConstraints.BOTH;
    return this;
  }

  @NotNull
  public GridBag fillCellHorizontally() {
    fill = GridBagConstraints.HORIZONTAL;
    return this;
  }

  @NotNull
  public GridBag fillCellVertically() {
    fill = GridBagConstraints.VERTICAL;
    return this;
  }

  public GridBag fillCellNone() {
    fill = GridBagConstraints.NONE;
    return this;
  }

  @NotNull
  public GridBag weightx(double weight) {
    weightx = weight;
    return this;
  }


  @NotNull
  public GridBag weighty(double weight) {
    weighty = weight;
    return this;
  }

  @NotNull
  public GridBag coverLine() {
    gridwidth = GridBagConstraints.REMAINDER;
    return this;
  }

  @NotNull
  public GridBag coverLine(int cells) {
    gridwidth = cells;
    return this;
  }

  @NotNull
  public GridBag coverColumn() {
    gridheight = GridBagConstraints.REMAINDER;
    return this;
  }

  @NotNull
  public GridBag coverColumn(int cells) {
    gridheight = cells;
    return this;
  }

  @NotNull
  public GridBag padx(int padding) {
    ipadx = padding;
    return this;
  }

  @NotNull
  public GridBag pady(int padding) {
    ipady = padding;
    return this;
  }


  /**
   * @see #insets(Insets)
   */
  @NotNull
  public GridBag insets(int top, int left, int bottom, int right) {
    return insets(JBUI.insets(top, left, bottom, right));
  }

  @NotNull
  public GridBag insetTop(int top) {
    return insets(JBUI.insets(top, -1, -1, -1));
  }

  @NotNull
  public GridBag insetBottom(int bottom) {
    return insets(JBUI.insets(-1, -1, bottom, -1));
  }

  @NotNull
  public GridBag insetLeft(int left) {
    return insets(JBUI.insets(-1, left, -1, -1));
  }

  @NotNull
  public GridBag insetRight(int right) {
    return insets(JBUI.insets(-1, -1, -1, right));
  }

  @NotNull
  public GridBag emptyInsets() {
    return insets(JBUI.emptyInsets());
  }

  /**
   * Pass -1 to use a default value for this column.
   * E.g, Insets(10, -1, -1, -1) means that 'top' will be changed to 10 and other sides will be set to defaults for this column.
   */
  @NotNull
  public GridBag insets(@Nullable Insets insets) {
    if (insets != null && (insets.top < 0 || insets.bottom < 0 || insets.left < 0 || insets.right < 0)) {
      Insets def = getDefaultInsets(gridx);
      insets = (Insets)insets.clone();
      if (insets.top < 0) insets.top = def == null ? 0 : def.top;
      if (insets.left < 0) insets.left = def == null ? 0 : def.left;
      if (insets.bottom < 0) insets.bottom = def == null ? 0 : def.bottom;
      if (insets.right < 0) insets.right = def == null ? 0 : def.right;
    }
    this.insets = insets;
    return this;
  }

  public int getDefaultAnchor() {
    return myDefaultAnchor;
  }

  @NotNull
  public GridBag setDefaultAnchor(int anchor) {
    myDefaultAnchor = anchor;
    return this;
  }

  public int getDefaultAnchor(int column) {
    return myDefaultColumnAnchors.containsKey(column) ? myDefaultColumnAnchors.get(column) : getDefaultAnchor();
  }

  @NotNull
  public GridBag setDefaultAnchor(int column, int anchor) {
    if (anchor == -1) {
      myDefaultColumnAnchors.remove(column);
    }
    else {
      myDefaultColumnAnchors.put(column, anchor);
    }
    return this;
  }

  public int getDefaultFill() {
    return myDefaultFill;
  }

  @NotNull
  public GridBag setDefaultFill(int fill) {
    myDefaultFill = fill;
    return this;
  }

  public int getDefaultFill(int column) {
    return myDefaultColumnFills.containsKey(column) ? myDefaultColumnFills.get(column) : getDefaultFill();
  }

  @NotNull
  public GridBag setDefaultFill(int column, int fill) {
    if (fill == -1) {
      myDefaultColumnFills.remove(column);
    }
    else {
      myDefaultColumnFills.put(column, fill);
    }
    return this;
  }

  public double getDefaultWeightX() {
    return myDefaultWeightX;
  }

  @NotNull
  public GridBag setDefaultWeightX(double weight) {
    myDefaultWeightX = weight;
    return this;
  }

  public double getDefaultWeightX(int column) {
    return myDefaultColumnWeightsX.containsKey(column) ? myDefaultColumnWeightsX.get(column) : getDefaultWeightX();
  }

  @NotNull
  public GridBag setDefaultWeightX(int column, double weight) {
    if (weight == -1) {
      myDefaultColumnWeightsX.remove(column);
    }
    else {
      myDefaultColumnWeightsX.put(column, weight);
    }
    return this;
  }


  public double getDefaultWeightY() {
    return myDefaultWeightY;
  }

  @NotNull
  public GridBag setDefaultWeightY(double weight) {
    myDefaultWeightY = weight;
    return this;
  }

  public double getDefaultWeightY(int column) {
    return myDefaultColumnWeightsY.containsKey(column) ? myDefaultColumnWeightsY.get(column) : getDefaultWeightY();
  }

  @NotNull
  public GridBag setDefaultWeightY(int column, double weight) {
    if (weight == -1) {
      myDefaultColumnWeightsY.remove(column);
    }
    else {
      myDefaultColumnWeightsY.put(column, weight);
    }
    return this;
  }


  public int getDefaultPaddingX() {
    return myDefaultPaddingX;
  }

  @NotNull
  public GridBag setDefaultPaddingX(int padding) {
    myDefaultPaddingX = padding;
    return this;
  }

  public int getDefaultPaddingX(int column) {
    return myDefaultColumnPaddingsX.containsKey(column) ? myDefaultColumnPaddingsX.get(column) : getDefaultPaddingX();
  }

  @NotNull
  public GridBag setDefaultPaddingX(int column, int padding) {
    if (padding == -1) {
      myDefaultColumnPaddingsX.remove(column);
    }
    else {
      myDefaultColumnPaddingsX.put(column, padding);
    }
    return this;
  }

  public int getDefaultPaddingY() {
    return myDefaultPaddingY;
  }

  @NotNull
  public GridBag setDefaultPaddingY(int padding) {
    myDefaultPaddingY = padding;
    return this;
  }

  public int getDefaultPaddingY(int column) {
    return myDefaultColumnPaddingsY.containsKey(column) ? myDefaultColumnPaddingsY.get(column) : getDefaultPaddingY();
  }

  @NotNull
  public GridBag setDefaultPaddingY(int column, int padding) {
    if (padding == -1) {
      myDefaultColumnPaddingsY.remove(column);
    }
    else {
      myDefaultColumnPaddingsY.put(column, padding);
    }
    return this;
  }

  @Nullable
  public Insets getDefaultInsets() {
    return myDefaultInsets;
  }

  @NotNull
  public GridBag setDefaultInsets(int top, int left, int bottom, int right) {
    return setDefaultInsets(JBUI.insets(top, left, bottom, right));
  }

  public GridBag setDefaultInsets(@Nullable Insets insets) {
    myDefaultInsets = insets;
    return this;
  }

  @Nullable
  public Insets getDefaultInsets(int column) {
    return myDefaultColumnInsets.containsKey(column) ? myDefaultColumnInsets.get(column) : getDefaultInsets();
  }

  @NotNull
  public GridBag setDefaultInsets(int column, int top, int left, int bottom, int right) {
    return setDefaultInsets(column, JBUI.insets(top, left, bottom, right));
  }

  @NotNull
  public GridBag setDefaultInsets(int column, @Nullable Insets insets) {
    if (insets == null) {
      myDefaultColumnInsets.remove(column);
    }
    else {
      myDefaultColumnInsets.put(column, insets);
    }
    return this;
  }
}
