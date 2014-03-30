package com.siyeh.ipp.trivialif.convert_to_nested_if;

class Nested {
  boolean foo(boolean a, boolean b, boolean c) {
      if (a) return true;
      if (b ^ c) if (a) return true;
      return false;
  }
}