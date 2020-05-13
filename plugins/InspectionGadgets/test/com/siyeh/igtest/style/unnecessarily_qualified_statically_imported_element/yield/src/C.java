package com.siyeh.igtest.style.unnecessarily_qualified_statically_imported_element;

import static java.lang.Thread.*;

class C {

  {
    Thread.yield();
    Thread.currentThread();
  }
}