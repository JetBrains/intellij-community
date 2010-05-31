class List<T> {}

class Base<T> {
  def fo<caret>o(String s, List<T>... l) {}
}

class Inheritor extends Base<Integer> {
  def foo(String s, List<Integer>... l) {}

  {
    new Inheritor().foo("a", new List<Integer>());
  }
}

