class Test {
  {
    Runnable r = () -> true <caret>? "a" : "b";
  }
}