import lib.Box;
import lib.Lib;

class ImplicitlyTypedLambda {
  interface Sink {
    void accept(String s);
  }

  void test(Lib lib, Box<Sink> box) {
    lib.foo(box, s -> {});
  }
}
