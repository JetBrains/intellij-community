public class MyTest {

    interface I {
        void meth(int i);
    }

    static class A {
        void m(int i) {}
    }

    static class B extends A {
        void m(int i1) {
            I i = (i2) -> super.m(i2);
        }
    }
}
