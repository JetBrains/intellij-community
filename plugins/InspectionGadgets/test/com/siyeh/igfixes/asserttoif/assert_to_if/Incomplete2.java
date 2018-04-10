package com.siyeh.ipp.asserttoif.assert_to_if;

public class Incomplete {

  void x(Object o) {
    <caret>assert bar(foo(info)
    boolean b = true;
  }
}

