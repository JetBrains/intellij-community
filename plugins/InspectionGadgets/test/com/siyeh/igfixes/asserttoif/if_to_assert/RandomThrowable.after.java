package com.siyeh.ipp.asserttoif.if_to_assert;

class RandomThrowable {
  void m(Object o) {
      assert o != null : "wtf?";
  }
}