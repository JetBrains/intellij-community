package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;


class Java9 {
  void test() throws FileNotFoundException {
    PrintStream printStream = new PrintStream("one");
      try (printStream) {
          printStream.print("dffd");
          printStream.print(true);
      }

  }
}