package com.siyeh.igtest.portability;

import java.net.URL;
import java.net.URI;

public class HardcodedFileSeparators {
  public HardcodedFileSeparators() {
  }

  public static void foo() {
    final String backSlash = " <warning descr="Hardcoded file separator '\'">\</warning><warning descr="Hardcoded file separator '\'">\</warning><warning descr="Hardcoded file separator '\'">\</warning><warning descr="Hardcoded file separator '\'">\</warning> ";
    final String slash = "<warning descr="Hardcoded file separator '/'">/</warning>";
    final String date = "dd/MM/yy";
    final String date2 = "sdd<warning descr="Hardcoded file separator '/'">/</warning>MM<warning descr="Hardcoded file separator '/'">/</warning>yy";
    final String tag1 = "<foo/>";
    final String tag2 = "</foo>";
    final String url = "jdbc:hsqldb:hsql://localhost:9013";
    final String withoutEscapeSeq = "<warning descr="Hardcoded file separator '/'">/</warning>" +
                                    "without" +
                                    "<warning descr="Hardcoded file separator '/'">/</warning>" +
                                    "escape" +
                                    "<warning descr="Hardcoded file separator '/'">/</warning>" +
                                    "sequences";
    final String withEscapeSeq = "<warning descr="Hardcoded file separator '/'">/</warning><warning descr="Hardcoded file separator '/'">/</warning>" +
                                  "with" +
                                  "\n" +
                                  "escape" +
                                  "<warning descr="Hardcoded file separator '\'">\</warning><warning descr="Hardcoded file separator '\'">\</warning>n" +
                                  "sequences" +
                                  "//\\";
  }

  void images() {
    this.getClass().getResource("/a/b/c/d/e.png");
    this.getClass().getResourceAsStream("/a/b/c/d/e.png");
  }

  void urls() throws Exception {
    new URL("http://jetbrains.com");
    new URI("http://jetbrains.com");
  }

  void textBlocks() {
    String withoutEscapeSeq =
      """
          C:<warning descr="Hardcoded file separator '\'">\</warning><warning descr="Hardcoded file separator '\'">\</warning>new_dir

      """;

    String withEscapeSeq =
      """
          \b

      """;
  }
}