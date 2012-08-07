package com.siyeh.igtest.style.size_replaceable_by_is_empty;

import java.util.Collection;

public class SizeReplaceableByIsEmpty {

  boolean foo(String s) {
    return s.length() == 0;
  }

  boolean bas(StringBuilder b) {
    return b.length() == 0;
  }

  boolean bar(Collection c) {
    return c.size() == 0;
  }

  class String {
    public int length() {
      return 1;
    }

    public boolean isEmpty() {
      return false;
    }
  }

  abstract class MyList<T> implements java.util.List<T>
  {
    public boolean isEmpty()
    {
      return this.size() == 0;
    }
  }
}
