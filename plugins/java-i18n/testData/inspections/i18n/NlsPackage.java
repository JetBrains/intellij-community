class Foo {
  void foo(String s) {}
  void bar(@org.jetbrains.annotations.NonNls String s) {}

  {
    foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>);
    bar("explicit nonnls");
  }
}