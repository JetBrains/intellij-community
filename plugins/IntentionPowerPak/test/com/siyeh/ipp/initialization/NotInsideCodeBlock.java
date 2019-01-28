class X {
  Object obj = new Object<caret>() {
    String toString() {
      String message = "foo";
      return message;
    }
  };
}