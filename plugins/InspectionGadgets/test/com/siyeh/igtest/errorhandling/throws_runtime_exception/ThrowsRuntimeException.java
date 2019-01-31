package com.siyeh.igtest.errorhandling.throws_runtime_exception;

public class ThrowsRuntimeException {

  void one() throws Throwable {}
  void two() throws <warning descr="Unchecked exception 'RuntimeException' declared in 'throws' clause">RuntimeException</warning> {}
  void three() throws <warning descr="Unchecked exception 'UnsupportedOperationException' declared in 'throws' clause">UnsupportedOperationException</warning> {}
  void four() throws Exception {}
}
