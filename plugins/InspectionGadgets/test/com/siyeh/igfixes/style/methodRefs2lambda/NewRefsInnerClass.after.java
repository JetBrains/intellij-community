class MyTest {
    static class Inner {
        Inner(MyTest mt) {};
    }
  
    interface I {
        Inner m(MyTest receiver);
    }

    static {
        I i1 = (mt) -> new Inner(mt);
    }
}
