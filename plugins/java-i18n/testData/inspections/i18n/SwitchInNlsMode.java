class Test {
  void foo(@org.jetbrains.annotations.Nls String s) {
    switch (s) {
      case <warning descr="Hardcoded string literal: \"foooooo\"">"foooooo"</warning>:
        break;
    }
  }
}