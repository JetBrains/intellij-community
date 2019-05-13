package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  boolean f(boolean a, boolean b, boolean c, boolean d) {
      if (a) return true;
      if (b) if (c) return true;
      if (d) return true;
      return false;
  }
}
