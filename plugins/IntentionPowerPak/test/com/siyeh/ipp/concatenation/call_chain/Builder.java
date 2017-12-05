class Test {
  {
    StringBuilder text = null;
    text.delete(text.length() - 1, text.length()).ap<caret>pend(";\n"); //keep me
  }
}