class X<T> {
  def foo(List<T> list, T t) {}
}

class Y extends X<String> {
  def foo(List<String> list, String t){}
}