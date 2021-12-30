package org.jetbrains.java.decompiler.modules.decompiler.typeann;

public class LocalvarTarget implements TargetInfo {
  private final Offsets[] table;

  public LocalvarTarget(Offsets[] table) {
    this.table = table;
  }

  public Offsets[] getTable() {
    return table;
  }

  public static class Offsets {
    private final int startPc;

    private final int length;

    private final int index;

    public Offsets(int startPc, int length, int index) {
      this.startPc = startPc;
      this.length = length;
      this.index = index;
    }

    public int getStartPc() {
      return startPc;
    }

    public int getLength() {
      return length;
    }

    public int getIndex() {
      return index;
    }
  }
}
