package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class SupertypeTarget implements TargetInfo {
  private final int supertypeIndex;

  public SupertypeTarget(int supertypeIndex) {
    this.supertypeIndex = supertypeIndex;
  }

  public int getSupertypeIndex() {
    return supertypeIndex;
  }
}
