package com.intellij.driver.model;

import com.intellij.driver.model.transport.PassByValue;

import java.awt.*;
import java.io.Serializable;

public final class TextData implements Serializable, PassByValue {
  private String text;
  private Point point;
  private String bundleKey;

  public TextData(String text, Point point, String bundleKey) {
    this.text = text;
    this.point = point;
    this.bundleKey = bundleKey;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public Point getPoint() {
    return point;
  }

  public void setPoint(Point point) {
    this.point = point;
  }

  public String getBundleKey() {
    return bundleKey;
  }

  public void setBundleKey(String bundleKey) {
    this.bundleKey = bundleKey;
  }

  @Override
  public String toString() {
    return "TextData{" +
           "text='" + text + '\'' +
           ", point=" + point +
           ", bundleKey='" + bundleKey + '\'' +
           '}';
  }
}
