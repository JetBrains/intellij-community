package com.siyeh.ipp.forloop.iterator;

import java.util.Collection;

class BareCollectionLoop {

  void x(Collection c) {
    fo<caret>r (Object n : c) {

    }
  }
}
