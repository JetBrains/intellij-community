package com.siyeh.igtest.errorhandling.throws_runtime_exception;

public class ThrowsRuntimeException {

  void one() throws Throwable {}
  void two() throws RuntimeException {}
  void three() throws UnsupportedOperationException {}
  void four() throws Exception {}
}
