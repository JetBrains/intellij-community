package com.siyeh.igtest.portability;

import java.net.*;

public class HardcodedFileSeparators {
  public HardcodedFileSeparators() {
  }

  public static void foo() {
    final String backSlash = "<warning descr="Hardcoded file separator '\\'"> \\ </warning>";
    final String slash = "<warning descr="Hardcoded file separator '/'">/</warning>";
    final String date = "dd/MM/yy";
    final String date2 = "<warning descr="Hardcoded file separator 'sdd/MM/yy'">sdd/MM/yy</warning>";
    final String tag1 = "<foo/>";
    final String tag2 = "</foo>";
    final String url = "jdbc:hsqldb:hsql://localhost:9013";
  }

  void m() {
    this.getClass().getResource("/a/b/c/d/e.png");
    this.getClass().getResourceAsStream("/a/b/c/d/e.png");
  }

  void n() throws Exception {
    new URL("http://jetbrains.com");
    new URI("http://jetbrains.com");
  }
}