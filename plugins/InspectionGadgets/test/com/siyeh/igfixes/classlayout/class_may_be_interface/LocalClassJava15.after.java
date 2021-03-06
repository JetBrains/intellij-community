package com.siyeh.igfixes.classlayout.class_may_be_interface;

class X {
  void test() {
    interface <caret>Foo {}
    
    class Bar implements Foo {}
  }
}