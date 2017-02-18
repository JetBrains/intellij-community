class MyTest {
  static class Inner {
      Inner(MyTest outer) {};
      Inner() {};
  }
  
  interface I {
      Inner m(MyTest receiver);
  }
    

  static {
      I i1 = MyTest.Inner:<caret>:new;
  }
}
