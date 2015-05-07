package com.siyeh.igtest.threading.call_to_native_method_while_locked;

public class CallToNativeMethodWhileLocked {

  synchronized void a() {
    Double.<warning descr="Call to native method 'doubleToLongBits()' in a synchronized context">doubleToLongBits</warning>(9.7);
    Runnable r = () -> {
      Double.doubleToLongBits(123.4);
    };
    new Object() {
      long l = Double.doubleToLongBits(42.0);
    };
  }
}
