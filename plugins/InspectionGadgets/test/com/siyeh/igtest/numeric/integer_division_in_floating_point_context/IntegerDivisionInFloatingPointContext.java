package com.siyeh.igtest.numeric.integer_division_in_floating_point_context;

public class IntegerDivisionInFloatingPointContext {
    public void foo()
    {
        float x = 3.0F + <warning descr="'3/5': integer division in floating-point context">3/5</warning>;
        double y = <warning descr="'1 / 2 / 3': integer division in floating-point context">1 / 2 / 3</warning>;
        double z = <warning descr="'1 / 2 / 3 / 4.0': integer division in floating-point context">1 / 2 / 3 / 4.0</warning>;
        double z1 = 1.0 / 2 / 3 / 4.0;
    }
}
