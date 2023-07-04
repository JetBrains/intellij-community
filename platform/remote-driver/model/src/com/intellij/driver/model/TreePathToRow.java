package com.intellij.driver.model;

import com.intellij.driver.model.transport.PassByValue;

import java.io.Serializable;
import java.util.List;

public final class TreePathToRow implements Serializable, PassByValue {
  private List<String> path;
  private int row;

  public TreePathToRow(List<String> path, int row) {
    this.path = path;
    this.row = row;
  }

  public List<String> getPath() {
    return path;
  }

  public void setPath(List<String> path) {
    this.path = path;
  }

  public int getRow() {
    return row;
  }

  public void setRow(int row) {
    this.row = row;
  }

  @Override
  public String toString() {
    return "TreePathToRow{" +
           "path=" + path +
           '}';
  }
}
