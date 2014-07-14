package com.siyeh.igtest.performance.object_allocation_in_loop;

class ObjectAllocationInLoop {

  void m() {
    while (true) {
      new <warning descr="Object allocation 'new Object()' in loop">Object</warning>();
    }
  }
}