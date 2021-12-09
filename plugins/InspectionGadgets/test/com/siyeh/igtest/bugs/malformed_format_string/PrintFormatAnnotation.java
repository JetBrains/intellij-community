package com.siyeh.igtest.bugs.malformed_format_string;

import org.intellij.lang.annotations.PrintFormat;

public class PrintFormatAnnotation {
  public native boolean execute(@PrintFormat String sql, Object[] args);
  public native boolean execute2(String mode, @PrintFormat String sql, Object... args);
  // Not supported: format string should be right before arguments
  public native boolean execute3(@PrintFormat String sql, String mode, Object... args);

  void test() {
    execute("SELECT %d FROM %s", new Object[] {<warning descr="Argument type 'String' does not match the type of the format specifier '%d'">"s"</warning>, 2});

    execute2("SELECT %d FROM %s", "SELECT %s FROM %d", "s", 2);

    execute2("SELECT %s FROM %d", "SELECT %d FROM %s", <warning descr="Argument type 'String' does not match the type of the format specifier '%d'">"s"</warning>, 2);

    execute3("SELECT %d FROM %s", "SELECT %s FROM %d", "s", 2);

    execute3("SELECT %s FROM %d", "SELECT %d FROM %s", "s", 2);
  }
}
