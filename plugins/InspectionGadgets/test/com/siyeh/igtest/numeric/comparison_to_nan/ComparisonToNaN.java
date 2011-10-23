package com.siyeh.igtest.numeric.comparison_to_nan;

import static java.lang.Double.*;

public class ComparisonToNaN {
    public void foo(double x)
    {
        if(x == Float.NaN)
        {
            return;
        }
        if (x == NaN) {
          return;
        }
    }
}
