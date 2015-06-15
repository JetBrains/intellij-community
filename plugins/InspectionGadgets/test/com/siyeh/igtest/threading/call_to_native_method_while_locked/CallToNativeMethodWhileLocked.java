package com.siyeh.igtest.threading.call_to_native_method_while_locked;

public class CallToNativeMethodWhileLocked {

  synchronized void a() {
    Double.<warning descr="Call to native method 'doubleToRawLongBits()' in a synchronized context">doubleToRawLongBits</warning>(9.7);
    Runnable r = () -> {
      Double.doubleToRawLongBits(123.4);
    };
    new Object() {
      long l = Double.doubleToRawLongBits(42.0);
    };
    Runnable s = () -> {
      assert Thread.holdsLock(this);
      Double.<warning descr="Call to native method 'doubleToRawLongBits()' in a synchronized context">doubleToRawLongBits</warning>(40.0);
    };
  }
}
