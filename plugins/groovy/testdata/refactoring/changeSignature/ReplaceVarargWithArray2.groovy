class Map<T, E> {}

class A<T> {
  def <E> fo<caret>o(int x, Map<T, E>... l) {
  }
}

class B extends A<String> {
  def <E> foo(int x, Map<String, E>... l) {
  }
}

new B<Integer>().foo(1, new Map<String, Integer>());