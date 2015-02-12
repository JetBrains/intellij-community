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