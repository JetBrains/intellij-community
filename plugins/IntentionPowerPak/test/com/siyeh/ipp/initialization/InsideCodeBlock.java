class X {
  Object obj = new Object() {
    String toString() {
      String message = "<caret>foo";
      return message;
    }
  };
}