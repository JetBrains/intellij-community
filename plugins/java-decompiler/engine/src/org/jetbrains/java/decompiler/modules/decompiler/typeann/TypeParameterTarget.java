package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class TypeParameterTarget implements TargetInfo {
  private final int typeParameterIndex;

  public TypeParameterTarget(int typeParameterIndex) {
    this.typeParameterIndex = typeParameterIndex;
  }

  public int getTypeParameterIndex() {
    return typeParameterIndex;
  }
}
