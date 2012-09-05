package com.siyeh.igtest.threading.synchronized_on_literal_object;

class SynchronizedOnLiteralObject {

  private Integer myInteger = (Integer)1;
  private Character c = 'a';

  void foo() {
    synchronized (myInteger) {}
    synchronized ("asdf") {}
    synchronized ((Boolean) true) {}
    synchronized (c) {}
  }
}