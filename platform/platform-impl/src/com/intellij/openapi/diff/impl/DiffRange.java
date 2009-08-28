package com.intellij.openapi.diff.impl;



/**
 * @author Jeka
 */
public class DiffRange implements DiffFragmentBuilder.Range {
  private final int myStart;
  private final int myEnd;

  public DiffRange(int start, int end) {
    myStart = start;
    myEnd = end;
  }

  public int getStart() {
    return myStart;
  }

  public int getEnd() {
    return myEnd;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "DiffRange: " + myStart + "," + myEnd;
  }
}