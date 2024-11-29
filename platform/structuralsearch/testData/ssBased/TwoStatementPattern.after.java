class Scratch {

  private String s = null;

  void foo() {
    s = "1";
      //noinspection SillyNullCheck
      s = "2";
    if (s == null) {
      throw new IllegalStateException("drunk");
    }
  }

  void noWarnings1() {
    s = "1";
    //noinspection SillyNullCheck
    s = "2";
    if (s == null) {
      throw new IllegalStateException("drunk");
    }
  }

  void noWarnings2() {
    s = "1";
    //noinspection SSBasedInspection
    s = "2";
    if (s == null) {
      throw new IllegalStateException("drunk");
    }
  }

}
