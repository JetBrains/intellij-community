package com.siyeh.igtest.numeric.integer_multiplication_implicit_cast_to_long;

public class IntegerMultiplicationImplicitCastToLong {
    public void foo() {
        int x = 65336;
        final long val = <warning descr="65336 * x: integer multiplication implicitly cast to long">65336 * x</warning>;
        long other = <warning descr="Integer.valueOf(65336) * Integer.valueOf(x): integer multiplication implicitly cast to long">Integer.valueOf(65336) * Integer.valueOf(x)</warning>;
        long third = <warning descr="x << 24: integer shift implicitly cast to long">x << 24</warning>;
        long polyadic = <warning descr="x * 1024 * 1024: integer multiplication implicitly cast to long">x * 1024 * 1024</warning>;
        long incomplete = x * x *<error descr="Expression expected">;</error>
    }
}
