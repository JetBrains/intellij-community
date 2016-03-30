class StructureView {
  private int data;

  public final int getData() {
    return data;
  }

  protected void setData(int data) {
    this.data = data;
  }

  public static class B {
    public static StructureView build(int data) {
      return new StructureView() {
        { setData(data); }
      };
    }
  }
}