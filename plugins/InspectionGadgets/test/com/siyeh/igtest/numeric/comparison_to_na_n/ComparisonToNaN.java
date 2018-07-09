package com.siyeh.igtest.numeric.comparison_to_nan;

import static java.lang.Double.*;

public class ComparisonToNaN {
    public void foo(double x)
    {
        if(x == <warning descr="Comparison to 'Float.NaN' is always false">Float.NaN</warning>)
        {
            return;
        }
        if (x == <warning descr="Comparison to 'NaN' is always false">NaN</warning>) {
          return;
        }
        if (x > <warning descr="Comparison to 'NaN' is always false">NaN</warning>) {
            return;
        }
        if (x <= <warning descr="Comparison to 'NaN' is always false">NaN</warning>) {
            return;
        }
        if (<warning descr="Comparison to 'Float.NaN' is always true">Float.NaN</warning> != Float.NaN) {
            return;
        }
    }
}
