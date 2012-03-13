package com.siyeh.ipp.forloop.iterator;

import java.util.Collection;

class BoundedTypes {
  void x(Collection<? extends Number> c) {
    <caret>for (Number n : c) {

    }
  }
}
