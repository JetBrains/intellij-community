class MyTest {
    class Inner {
        Inner() {};
    }
  
    interface I {
        Inner m(MyTest receiver);
    }

    static {
        I i1 = (receiver) -> receiver.new Inner();
    }
}
