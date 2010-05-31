class List<T> {}

class Base<T> {
  def foo(List<T>[] l, String s) {}
}

class Inheritor extends Base<Integer> {
  def foo(List<Integer>[] l, String s) {}

  {
    new Inheritor().foo([new List<Integer>()] as List<Integer>[], "a");
  }
}

