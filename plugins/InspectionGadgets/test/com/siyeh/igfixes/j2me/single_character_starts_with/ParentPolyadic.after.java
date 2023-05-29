class MyTest {

  boolean x(String s) {
      /*c1*/
      return !s.isEmpty() && s.charAt(0) == 'x'<caret>//c2 
              &&
              !s.endsWith("x");
  }
}