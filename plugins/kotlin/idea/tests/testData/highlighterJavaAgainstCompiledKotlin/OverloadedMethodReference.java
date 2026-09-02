import lib.Box;
import lib.OverloadedLib;

class OverloadedMethodReference {
  interface Action {
    void run();
  }

  static void target() {}

  void test(OverloadedLib lib, Box<Action> box) {
    lib.foo(box, OverloadedMethodReference::target);
  }
}
