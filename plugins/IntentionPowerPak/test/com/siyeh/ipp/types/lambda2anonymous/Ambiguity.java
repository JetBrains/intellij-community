interface I1 {
  void m();
}

interface I2<X> {
  X m();
}

class Ambiguity1 {

  static void m(I1 i1) {}
  static <T> void m(I2<T> i2) {}

  {
    m((<caret>)->{throw new AssertionError();});
  }
}
