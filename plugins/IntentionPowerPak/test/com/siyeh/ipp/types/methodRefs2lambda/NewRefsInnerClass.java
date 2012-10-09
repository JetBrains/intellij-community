class MyTest {
    class Inner {
        Inner() {};
    }
  
    interface I {
        Inner m(MyTest receiver);
    }

    static {
        I i1 = MyTest.Inner:<caret>:new;
    }
}
