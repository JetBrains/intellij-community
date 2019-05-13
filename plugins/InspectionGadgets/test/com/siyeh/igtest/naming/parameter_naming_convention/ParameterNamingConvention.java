package com.siyeh.igtest.naming.parameter_naming_convention;

public class ParameterNamingConvention {

  void m(int <warning descr="Parameter name 'a' is too short (1 < 3)">a</warning>) {}
  void n(int abcd) {
    F f = (i) -> 10;
  }

  interface F {
    int a(int <warning descr="Parameter name 'i' is too short (1 < 3)">i</warning>);
  }

}
