package com.siyeh.igtest.numeric.confusing_floating_point_literal;

public class ConfusingFloatingPointLiteral
{
    double baz = <warning descr="Confusing floating point literal '0e6'">0e6</warning>;
    double baz2 = <warning descr="Confusing floating point literal '0.e6'">0.e6</warning>;
    double baz3 = 0.0e6;
    double baz4 = 0.0e-6;
    double baz5 = 0.0e-6;
    double foo = 3.0;
    double barangus2 = <warning descr="Confusing floating point literal '.03'">.03</warning>;
    double barangus = <warning descr="Confusing floating point literal '3.'">3.</warning>;
    float bazf = <warning descr="Confusing floating point literal '0e6f'">0e6f</warning>;
    float baz2f = <warning descr="Confusing floating point literal '0.e6f'">0.e6f</warning>;
    float baz3f = 0.0e6f;
    float foof = 3.0f;
    float barangus2f = <warning descr="Confusing floating point literal '.03f'">.03f</warning>;
    float barangusf = <warning descr="Confusing floating point literal '3.f'">3.f</warning>;

    double good = 0x1.0p10;
    double plusGood = 0x1.0p10d;
    float orDrown = <warning descr="Confusing floating point literal '0x.1p1f'">0x.1p1f</warning>;
    double hex = 0xA.Ap0;
    double gm = 3.986_004_418e14; // (m3/s2) gravitational parameter
    double thousand = 1_000.0;
}
