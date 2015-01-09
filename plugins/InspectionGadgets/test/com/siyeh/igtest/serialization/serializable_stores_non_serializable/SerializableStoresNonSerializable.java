class SerializableStoresNonSerializable {

  void f(final B b, final C c) {
    new Object() {
      @Override
      public String toString() {
        System.out.println(c);
        System.out.println(b);
        return super.toString();
      }
    };
    new A() {
      @Override
      public void m() {
        System.out.println(c);
        System.out.println(<warning descr="Serializable anonymous class implicitly stores non-Serializable object of type 'B'">b</warning>);
      }
    };
    A a = () -> {
      System.out.println(c);
      System.out.println(<warning descr="Serializable lambda implicitly stores non-Serializable object of type 'B'">b</warning>);
    };
    class D implements A {
      @Override
      public void m() {
        System.out.println(c);
        System.out.println(<warning descr="Serializable local class 'D' implicitly stores non-Serializable object of type 'B'">b</warning>);
      }
    }
  }
}
interface A extends java.io.Serializable {
  void m();
}
class B {}
class C implements java.io.Serializable {}