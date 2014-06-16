package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  boolean f(boolean a, boolean b, boolean c, boolean d) {
      if (a) if (b || c) if (d) return true;
      return false;
  }
}
