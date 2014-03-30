package com.siyeh.ipp.asserttoif.assert_to_if;

public class Incomplete {

  void x(Object o) {
      if (o == null) throw new AssertionError();
  }
}
