package com.siyeh.igtest.bugs.ignore_result_of_call;

class IgnoreResultOfCall {

  void foo(Object o, String s) {
    o.equals(s);
    o.equals()
  }

}