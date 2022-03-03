package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class FormalParameterTarget implements TargetInfo {
  private final int formalParameterIndex;

  public FormalParameterTarget(int formalParameterIndex) {
    this.formalParameterIndex = formalParameterIndex;
  }

  public int getFormalParameterIndex() {
    return formalParameterIndex;
  }
}
