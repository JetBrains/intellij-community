package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class This implements Iterable {

  void m() {
      for (Object o: this) {
          System.out.println(o);
      }
  }

  @Override
  public Iterator iterator() {
    return null;
  }
}
