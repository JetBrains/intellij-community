def foo(String s, int x = 5, Str<caret>ing... vars) {
  print s + x + vars;
}

foo("a", "b", "c")