import lib.Box;
import lib.OverloadedLib;

class OverloadedLambda {
  interface Action {
    void run();
  }

  void test(OverloadedLib lib, Box<Action> box) {
    lib.foo(box, () -> {});
  }
}
