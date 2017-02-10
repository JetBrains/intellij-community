package com.siyeh.igfixes.performance.concatenation_inside_append;

import java.io.PrintWriter;

class PrintWriterAppend {

  void foo(PrintWriter printWriter, int year, int season) {
    printWriter.app<caret>end("this is intellij idea " + year + "." + season + " version");
  }
}