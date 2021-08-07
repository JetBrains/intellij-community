public class Foo {
  public static class Bar {}
}

class Goo {
  void test(){
    Supplier<Foo.Bar> s = Foo.Bar:<caret>:new;
  }
}

interface Supplier<T> {
  T get();
}