class Foo {
  void foo(@org.jetbrains.annotations.Nls String s) {}
  void bar(String s) {}

  {
    foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>);
    foo("text"); //NON-NLS
    bar("text");
  }
}