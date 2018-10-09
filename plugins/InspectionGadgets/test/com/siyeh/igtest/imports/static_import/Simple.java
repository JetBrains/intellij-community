package com.siyeh.igtest.imports.static_import;

<warning descr="Static import 'import static java.lang.Math.sin;'">import static java.lang.Math.sin;</warning>
import static java.util.Map.Entry;

class Simple {

  void f00() {
    sin(1.0);
    Entry entry;
  }
}