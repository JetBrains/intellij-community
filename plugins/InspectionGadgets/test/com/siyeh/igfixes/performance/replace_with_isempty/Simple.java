package com.siyeh.igfixes.performance.replace_with_isempty;

public class Simple {

  void foo(String s) {
    if (s.eq<caret>uals("")) {}
  }

}
