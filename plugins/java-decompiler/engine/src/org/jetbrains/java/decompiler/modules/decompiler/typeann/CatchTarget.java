package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class CatchTarget implements TargetInfo {
  private final int exceptionTableIndex;

  public CatchTarget(int exceptionTableIndex) {
    this.exceptionTableIndex = exceptionTableIndex;
  }

  public int getExceptionTableIndex() {
    return exceptionTableIndex;
  }
}
