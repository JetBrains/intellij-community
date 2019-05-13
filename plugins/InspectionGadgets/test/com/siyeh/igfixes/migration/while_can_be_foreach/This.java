package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class This implements Iterable {

  void m() {
    final Iterator iterator = iterator();
    <caret>while (iterator.hasNext()) {
      System.out.println(iterator.next());
    }
  }

  @Override
  public Iterator iterator() {
    return null;
  }
}
