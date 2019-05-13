package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;


class Java9 {
  void test() throws FileNotFoundException {
    PrintStream printStream = new PrintStream("one");
    printStream.print("dffd");
    try<caret> {
      printStream.print(true);
    } finally {
      printStream.close();
    }

  }
}