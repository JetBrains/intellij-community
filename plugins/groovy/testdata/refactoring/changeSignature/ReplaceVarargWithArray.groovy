class List<T> {}

class Base<T> {
  void fo<caret>o(String s, List<T>... l) {}
}

class Inheritor extends Base<Integer> {
  void foo(String s, List<Integer>... l) {}

  {
    new Inheritor().foo("a", new ArrayList<Integer>());
  }
}

