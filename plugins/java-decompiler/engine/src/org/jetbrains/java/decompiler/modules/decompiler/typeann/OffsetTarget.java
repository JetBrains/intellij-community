package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class OffsetTarget implements TargetInfo {
  private final int offset;

  public OffsetTarget(int offset) {
    this.offset = offset;
  }

  public int getOffset() {
    return offset;
  }
}
