package com.siyeh.igtest.performance.object_allocation_in_loop;

import java.util.regex.*;

class ObjectAllocationInLoop {

  void m() {
    while (true) {
      new <warning descr="Object allocation 'new Object()' in loop">Object</warning>();
    }
  }

  void m1() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      if(sb != null) {
        sb.append(i);
      } else {
        sb = new StringBuilder(String.valueOf(i));
      }
    }
  }

  void m2() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      if (sb == null) {
        sb = new StringBuilder(String.valueOf(i));
      }
      else {
        sb.append(i);
      }
    }
  }

  boolean checkPatterns(String[] patterns) {
    for (String pattern : patterns) {
      try {
        Pattern.<warning descr="Indirect object allocation via 'compile()' call in loop">compile</warning>(pattern);
      }
      catch (PatternSyntaxException exception) {
        return false;
      }
    }
    return true;
  }
}