package com.siyeh.igtest.classlayout.noop_method_in_abstract_class;

abstract class NoopMethodInAbstractClass {

  void foo() {}

  native int bar();

  final void noop() {}
}