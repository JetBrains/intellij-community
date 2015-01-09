package com.siyeh.igtest.style.unnecessary_final_on_local_variable_or_parameter;

public class UnnecessaryFinalOnLocalVariableOrParameter {
  class XX {
    XX(Object o) {}

    void foo(final Object o) {
      new XX(o) {};
    }

    void m(final Object o) {
      new XX(null) {
        Object b = o;
      };
    }
    
    void fx(final Object o) {
      new XX(new XX(null) {
        Object b = o;
      });
    }
  }
}