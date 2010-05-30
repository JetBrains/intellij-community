class List<T> {}

class Base<T> {
  void foo(List<T>[] l, String s) {}
}

class Inheritor extends Base<Integer> {
  void foo(List<Integer>[] l, String s) {}

  {
    new Inheritor().foo([new List<Integer>()] as List<Integer>[], "a");
  }
}

