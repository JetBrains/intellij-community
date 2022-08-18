package com.siyeh.igtest.naming.enumerated_constant_naming_convention;

enum EnumeratedConstantNamingConvention {
  A_B_C,
  <warning descr="Enum constant name 'A' is too short (1 < 5)">A</warning>,
  <warning descr="Enum constant name 'aaaaaa' doesn't match regex '[A-Z][A-Z_\d]*'">aaaaaa</warning>
}