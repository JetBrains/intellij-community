package com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui;

import javax.swing.*;

/**
 * author: lesya
 */

class IntegerSpinnerModel extends AbstractSpinnerModel {
  private final int myMinValue;
  private final int myMaxValue;
  private int myValue = 0;

  public IntegerSpinnerModel(int myMinValue, int myMaxValue) {
    this.myMinValue = myMinValue;
    this.myMaxValue = myMaxValue;
  }

  public Object getNextValue() {
    int result;
    if (myMaxValue >= 0 && myValue == myMaxValue) {
      if (myMinValue < 0) return null;
      result = myMinValue;
    } else {
      result = myValue + 1;
    }

    return new Integer(result);
  }

  public Object getPreviousValue() {
    int result;
    if (myMinValue >= 0 && myValue == myMinValue) {
      if (myMaxValue < 0) return null;
      result = myMaxValue;
    } else {
      result = myValue - 1;
    }
    return new Integer(result);
  }

  public Object getValue() {
    return String.valueOf(myValue);
  }

  public void setValue(Object value) {
    if (value instanceof String) setStringValue((String) value);
    if (value instanceof Integer) setIntegerValue((Integer) value);
    fireStateChanged();
  }

  private void setIntegerValue(Integer integer) {
    myValue = ((Integer) integer).intValue();
  }

  private void setStringValue(String s) {
    myValue = Integer.parseInt(s);
  }

  public int getIntValue() {
    return myValue;
  }

}

