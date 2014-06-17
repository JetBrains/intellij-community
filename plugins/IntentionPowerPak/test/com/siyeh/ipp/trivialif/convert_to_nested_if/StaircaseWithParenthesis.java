package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  public static boolean foo(double a, double b, double c)
  {
    // the following return statement is converted by "Convert to multiple 'ifs'" (on the second &&) to the below, incorrect if-then-else
    return (a > c && a < b) &<caret>& !bar1(a) && !bar2(a);
  }

  private static boolean bar1(double a)
  {
    return true;
  }

  private static boolean bar2(double a)
  {
    return true;
  }
}
