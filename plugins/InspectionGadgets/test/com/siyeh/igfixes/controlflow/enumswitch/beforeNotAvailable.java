// "Fix all 'Enum 'switch' statement that misses case' problems in file" "false"
enum E {
  foo, bar;

  private E e;

  int foo() {
    switch (this) {
      case foo: return 1;
      case bar: return 2;
      <caret>default: return 3;
    }
  }
}