package com.siyeh.ipp.forloop.iterator;

import java.util.Collection;
import java.util.Iterator;

class BoundedTypes {
  void x(Collection<? extends Number> c) {
      <caret>for (Iterator<? extends Number> iterator = c.iterator(); iterator.hasNext(); ) {
          var n = iterator.next();

      }
  }
}
