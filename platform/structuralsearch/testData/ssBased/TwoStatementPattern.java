class Scratch {

  private String s = null;

  void foo() {
    s = "1";
    <warning descr="silly null check">s = "2";</warning>
    if (s == null) {
      throw new IllegalStateException("drunk");
    }
  }

}
