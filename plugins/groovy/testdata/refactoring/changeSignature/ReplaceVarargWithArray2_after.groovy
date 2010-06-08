class Map<T, E> {}

class A<T> {
  def <E> foo(Map<T, E>[] l, int x) {
  }
}

class B extends A<String> {
  def <E> foo(Map<String, E>[] l, int x) {
  }
}

new B<Integer>().foo([new Map<String, Integer>()] as Map<String, Integer>[], 1);