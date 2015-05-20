class Test {
  {
    I r = (i) -> i > 0 <caret>? "a" : "b";
  }
  interface I {
    String f(int i);
  } 
}