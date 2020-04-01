// "Create missing branches: 'E1', and 'E2'" "true"
class Foo {
  void foo(E e) {
      switch (e) {
          case E1:
              break;
          case E2:
              break;
      }
  }
}

enum E {
  E1, E2;
}