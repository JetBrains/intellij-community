package com.intellij.ui;

public class RightMarginEditorFeature extends EditorFeature {
  private int myRightMarginColumns;

  public RightMarginEditorFeature(boolean enabled, int rightMarginColumns) {
    super(enabled);
    myRightMarginColumns = rightMarginColumns;
  }

  public int getRightMarginColumns() {
    return myRightMarginColumns;
  }
}
