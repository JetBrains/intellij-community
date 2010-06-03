abstract class X {
  abstract def foo(String s, int a)

  def foo(String s) {
    return foo(s, 5);
  }
}

class Y extends X {
  def foo(String s, int a) {

  }
}
