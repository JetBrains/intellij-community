package com.siyeh.ipp.junit.flip_assert_literal;

import org.junit.Assert;

class Messsage {
  void test(boolean b) {
      Assert.assertFalse("message", b);
  }
}