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