// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import java.awt.*;

/**
 * Util around {@link GridBagConstraints} to avoid long repetitive configuration blocs.
 */
public class GridBagConstraintBuilder {

  private final GridBagConstraints constraint;

  public GridBagConstraintBuilder() {
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
  public GridBagConstraintBuilder fillX() {
    constraint.fill = GridBagConstraints.HORIZONTAL;
    return this;
  }

  /**
   * Makes elements resize vertically.
   */
  public GridBagConstraintBuilder fillY() {
    constraint.fill = GridBagConstraints.VERTICAL;
    return this;
  }

  /**
   * Makes elements resize horizontally and vertically.
   */
  public GridBagConstraintBuilder fillXY() {
    constraint.fill = GridBagConstraints.BOTH;
    return this;
  }

  /**
   * Stops elements resizing.
   */
  public GridBagConstraintBuilder noFill() {
    constraint.fill = GridBagConstraints.NONE;
    return this;
  }

  /**
   * Puts elements in a new line.
   */
  public GridBagConstraintBuilder newLine() {
    constraint.gridy += 1;
    return this;
  }

  /**
   * Makes cells grow horizontally.
   */
  public GridBagConstraintBuilder growX() {
    constraint.weightx = 1.0;
    constraint.weighty = 0.0;
    return this;
  }

  /**
   * Makes cells grow according to weightx and weighty.
   */
  public GridBagConstraintBuilder weight(double weightx, double weighty) {
    constraint.weightx = weightx;
    constraint.weighty = weighty;
    return this;
  }

  /**
   * Makes cells grow vertically.
   */
  public GridBagConstraintBuilder growY() {
    constraint.weightx = 0.0;
    constraint.weighty = 1.0;
    return this;
  }

  /**
   * Makes cells grow horizontally and vertically.
   */
  public GridBagConstraintBuilder growXY() {
    constraint.weightx = 1.0;
    constraint.weighty = 1.0;
    return this;
  }

  /**
   * Stops cells growing.
   */
  public GridBagConstraintBuilder noGrow() {
    constraint.weightx = 0.0;
    constraint.weighty = 0.0;
    return this;
  }

  /**
   * Changes the width of cells within the grid.
   */
  public GridBagConstraintBuilder width(int w) {
    constraint.gridwidth = w;
    return this;
  }

  /**
   * Removes cells insets.
   */
  public GridBagConstraintBuilder noInsets() {
    constraint.insets.set(0, 0, 0, 0);
    return this;
  }

  /**
   * Adds insets to cells.
   */
  public GridBagConstraintBuilder insets(int top, int left, int bottom, int right) {
    constraint.insets.set(top, left, bottom, right);
    return this;
  }

  /**
   * Makes cells stick to the end of the line.
   */
  public GridBagConstraintBuilder anchorEnd() {
    constraint.anchor = GridBagConstraints.LINE_END;
    return this;
  }

  /**
   * Makes cells stick to the start of the line.
   */
  public GridBagConstraintBuilder anchorStart() {
    constraint.anchor = GridBagConstraints.LINE_START;
    return this;
  }

  /**
   * Makes cells stick to the center of the line.
   */
  public GridBagConstraintBuilder anchorCenter() {
    constraint.anchor = GridBagConstraints.CENTER;
    return this;
  }

  /**
   * Makes cells stick horizontally along the trailing edge.
   * See {@link GridBagConstraints#BASELINE_TRAILING}.
   */
  public GridBagConstraintBuilder anchorTrailing() {
    constraint.anchor = GridBagConstraints.BASELINE_TRAILING;
    return this;
  }
}
