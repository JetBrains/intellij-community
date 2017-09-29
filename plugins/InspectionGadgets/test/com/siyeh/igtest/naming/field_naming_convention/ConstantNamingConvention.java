package com.siyeh.igtest.naming.constant_naming_convention;

class ConstantNamingConvention {
  static final String A_B_C_D3 = "";
  static final String <warning descr="Constant name 'a' is too short (1 < 5)">a</warning> = "";
  static final String <warning descr="Constant name 'aaaaaa' doesn't match regex '[A-Z][A-Z_\d]*'">aaaaaa</warning> = "";
}