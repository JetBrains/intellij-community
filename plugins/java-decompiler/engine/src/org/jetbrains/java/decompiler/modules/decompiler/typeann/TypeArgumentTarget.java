package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class TypeArgumentTarget implements TargetInfo {
  private final int offset;

  private final int typeArgumentIndex;

  public TypeArgumentTarget(int offset, int typeArgumentIndex) {
    this.offset = offset;
    this.typeArgumentIndex = typeArgumentIndex;
  }

  public int getOffset() {
    return offset;
  }

  public int getTypeArgumentIndex() {
    return typeArgumentIndex;
  }
}
