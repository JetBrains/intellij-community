class AssertCheckBefore {
  void m(Object child, Object parent) {
    if (parent instanceof Number) {
      if (child instanceof String) {
        assertTrue("", parent instanceof Integer, false);
        Integer attribute = (Integer) parent;
      }
    }
    if (parent instanceof Number) {
      if (child instanceof String) {
        assertTrue("", false, parent instanceof Integer);
        Integer attribute = <warning descr="Cast '(Integer) parent' conflicts with surrounding 'instanceof' check">(Integer) parent</warning>;
      }
    }
    if (parent instanceof Number) {
      if (child instanceof String) {
        assertFalse(parent instanceof Integer);
        Integer attribute = <warning descr="Cast '(Integer) parent' conflicts with surrounding 'instanceof' check">(Integer) parent</warning>;
      }
    }
    if (parent instanceof Number) {
      if (child instanceof String) {
        assertFalse(!(parent instanceof Integer));
        Integer attribute = (Integer) parent;
      }
    }
  }
  
  static void assertTrue(String message, boolean value, boolean wrongParameter) {
    if(!value) {
      throw new RuntimeException(message);
    }
  }
  
  static void assertFalse(boolean value) {
    if(value) {
      throw new RuntimeException();
    }
  }
}