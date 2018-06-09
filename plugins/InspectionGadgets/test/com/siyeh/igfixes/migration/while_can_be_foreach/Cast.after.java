package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class Cast {
  void m(List ss) {
      for (String s: (Iterable<String>) ss) {
          System.out.println(s);
      }
  }
}
