package com.siyeh.igtest.inheritance.abstract_method_with_missing_implementations;

class WithDefaultMethods {
      interface A {
          default void foo() {}
      }
  
      class B implements A {}

      interface C {
          void foo();
      }
      interface D extends C {
          default void foo(){}
      }
      class E implements C, D {}
}
class AbstractMethodWithMissingImplementations {
  interface A {
    void <warning descr="Abstract method 'foo()' is not implemented in every subclass">foo</warning>();
  }

  <error descr="Class 'B' must either be declared abstract or implement abstract method 'foo()' in 'A'">class B implements A</error> {}
}
interface EnumInterface {
  void method();
}

enum EnumImplTest implements EnumInterface {
  ONE {
    @Override
    public void method() {
    }
  },

  TWO {
    @Override
    public void method() {
    }
  }
}
interface I {
  void method();
}
enum E implements I {
  A,B;

  @Override
  public void method() {

  }
}
interface I2 {
  void <warning descr="Abstract method 'method()' is not implemented in every subclass">method</warning>();
}
<error descr="Class 'E2' must either be declared abstract or implement abstract method 'method()' in 'I2'">enum E2 implements I2</error> {
  A
}