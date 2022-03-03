package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class ThrowsTarget implements TargetInfo {
  private final int throwsTypeIndex;

  public ThrowsTarget(int throwsTypeIndex) {
    this.throwsTypeIndex = throwsTypeIndex;
  }

  public int getThrowsTypeIndex() {
    return throwsTypeIndex;
  }
}
