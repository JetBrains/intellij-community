class Foo {
  void foo(@org.jetbrains.annotations.Nls String s) {}
  void bar(String s) {}

  {
    foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>);
    foo("text"); //NON-NLS
    bar("text");
  }

  void test(@org.jetbrains.annotations.Nls String str) {
    if (str.equals(<warning descr="Hardcoded string literal: \"Hello World\"">"Hello World"</warning>)) {}
    if (str.startsWith(<warning descr="Hardcoded string literal: \"Hello World\"">"Hello World"</warning>)) { }
  }
}