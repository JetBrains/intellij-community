class X<T> {
  def f<caret>oo(T t) {} 
}

class Y extends X<String> {
  def foo(String t){}
}