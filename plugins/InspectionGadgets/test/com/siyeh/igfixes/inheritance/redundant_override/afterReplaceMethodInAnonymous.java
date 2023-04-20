// "Replace method with delegate to super" "true-preview"
interface I {
  default void foo(int x){}
}

class Test {
  void test(){
    new I() {
      public void foo(int x) {
          I.super.foo(x);
      }
    };
  }
}