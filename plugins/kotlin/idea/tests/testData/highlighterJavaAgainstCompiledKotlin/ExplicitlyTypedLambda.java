import lib.Box;
import lib.Lib;

class ExplicitlyTypedLambda {
  interface Action {
    void run();
  }

  void test(Lib lib, Box<Action> box) {
    lib.foo(box, () -> {});
  }
}
