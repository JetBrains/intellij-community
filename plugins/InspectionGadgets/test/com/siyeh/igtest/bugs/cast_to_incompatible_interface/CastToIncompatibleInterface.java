import java.util.List;

class CastToIncompatibleInterface {

  interface A {}
  interface B {
    default boolean acting() {
      return true;
    }
  }
  interface Z extends B {}
  class C {}

  boolean x(C c) {
    if (c instanceof Z) {
      Z z = ((Z)c);
    }
    if (c instanceof Z) {
      A a = ((<warning descr="Cast to incompatible interface 'A'">A</warning>)c);
    }
    if (c instanceof A) {
      A a = ((A)c);
    }
    if (c instanceof Z) {
      B b = ((B)c);
    }
    if (c instanceof B) {
      B b = ((B)c);
    }
    return c instanceof B && ((B)c).acting();
  }

  void x(String s) {
    List l = <error descr="Inconvertible types; cannot cast 'java.lang.String' to 'java.util.List'">(List)s</error>;
  }
  
  void testUnderInstanceOf() {
    if (getC(0) instanceof B && ((B)getC(0)).acting()) {}
    if (getC(0) instanceof B && ((<warning descr="Cast to incompatible interface 'B'">B</warning>)getC(1)).acting()) {}
  }

  interface Generic<T> {}
  class NonGeneric {}

  volatile NonGeneric ng;

  void testUnchecked() {
    if (ng instanceof Generic) {
      Generic<String> generic = (Generic<String>) ng;
    }
  }
  
  native C getC(int x);
}