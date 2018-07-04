package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  public static boolean foo(double a, double b, double c)
  {
    // the following return statement is converted by "Convert to multiple 'ifs'" (on the second &&) to the below, incorrect if-then-else
      if (a > c) if (a < b) if (!bar1(a//comment in bar1
      )) if (!bar2(//comment in bar2
              a)) return true;/*inside nested*///comment after first condition
//after end
      return false;
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
