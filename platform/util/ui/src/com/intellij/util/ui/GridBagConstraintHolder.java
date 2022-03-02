// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import java.awt.*;

/**
 * Util around {@link GridBagConstraints} to avoid long repetitive configuration blocs.
 */
public class GridBagConstraintHolder {

  private final GridBagConstraints constraint;

  public GridBagConstraintHolder() {
    constraint = new GridBagConstraints();
    constraint.gridx = -1;
    constraint.gridy = 0;
  }

  public GridBagConstraints get() {
    return constraint;
  }

  /**
   * Makes elements resize horizontally.
   */
  public GridBagConstraintHolder fillX() {
    constraint.fill = GridBagConstraints.HORIZONTAL;
    return this;
  }

  /**
   * Makes elements resize vertically.
   */
  public GridBagConstraintHolder fillY() {
    constraint.fill = GridBagConstraints.VERTICAL;
    return this;
  }

  /**
   * Makes elements resize horizontally and vertically.
   */
  public GridBagConstraintHolder fillXY() {
    constraint.fill = GridBagConstraints.BOTH;
    return this;
  }

  /**
   * Stops elements resizing.
   */
  public GridBagConstraintHolder noFill() {
    constraint.fill = GridBagConstraints.NONE;
    return this;
  }

  /**
   * Puts elements in a new line.
   */
  public GridBagConstraintHolder newLine() {
    constraint.gridy += 1;
    return this;
  }

  /**
   * Makes cells grow horizontally.
   */
  public GridBagConstraintHolder growX() {
    constraint.weightx = 1.0;
    constraint.weighty = 0.0;
    return this;
  }

  /**
   * Makes cells grow vertically.
   */
  public GridBagConstraintHolder growY() {
    constraint.weightx = 0.0;
    constraint.weighty = 1.0;
    return this;
  }

  /**
   * Makes cells grow horizontally and vertically.
   */
  public GridBagConstraintHolder growXY() {
    constraint.weightx = 1.0;
    constraint.weighty = 1.0;
    return this;
  }

  /**
   * Stops cells growing.
   */
  public GridBagConstraintHolder noGrow() {
    constraint.weightx = 0.0;
    constraint.weighty = 0.0;
    return this;
  }

  /**
   * Changes the width of cells within the grid.
   */
  public GridBagConstraintHolder width(int w) {
    constraint.gridwidth = w;
    return this;
  }

  /**
   * Removes cells insets.
   */
  public GridBagConstraintHolder noInsets() {
    constraint.insets.set(0, 0, 0, 0);
    return this;
  }

  /**
   * Adds insets to cells.
   */
  public GridBagConstraintHolder insets(int top, int left, int bottom, int right) {
    constraint.insets.set(top, left, bottom, right);
    return this;
  }

  /**
   * Adds insets to cells top, left, and bottom sides.
   */
  public GridBagConstraintHolder insetsTLB(int topLeftBottom) {
    constraint.insets.set(topLeftBottom, topLeftBottom, topLeftBottom, 0);
    return this;
  }

  /**
   * Makes cells stick to the end of the line.
   */
  public GridBagConstraintHolder anchorEnd() {
    constraint.anchor = GridBagConstraints.LINE_END;
    return this;
  }
}
