class MyTest {
  static class Inner {
      Inner(MyTest outer) {};
      Inner() {};
  }
  
  interface I {
      Inner m(MyTest receiver);
  }
    

  static {
      I i1 = (outer) -> new Inner(outer);
  }
}
