package com.siyeh.igtest.inheritance.abstract_method_with_missing_implementations;

class WithDefaultMethods {
      interface A {
          default void foo() {}
      }
  
      class B implements A {}
}