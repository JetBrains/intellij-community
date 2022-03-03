package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class TypeParameterBoundTarget implements TargetInfo {
  private final int typeParameterIndex;

  private final int boundIndex;

  public TypeParameterBoundTarget(int typeParameterIndex, int boundIndex) {
    this.typeParameterIndex = typeParameterIndex;
    this.boundIndex = boundIndex;
  }

  public int getTypeParameterIndex() {
    return typeParameterIndex;
  }

  public int getBoundIndex() {
    return boundIndex;
  }
}
