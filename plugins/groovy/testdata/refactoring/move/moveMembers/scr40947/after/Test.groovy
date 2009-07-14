class A {



}

class Test {
  static void bar() {}

  static void foo() {
    bar(); // note redundant "A" qualifier
  }

}