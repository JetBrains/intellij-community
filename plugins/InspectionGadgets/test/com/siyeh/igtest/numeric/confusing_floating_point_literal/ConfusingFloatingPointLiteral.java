package com.siyeh.igtest.numeric.confusing_floating_point_literal;

public class ConfusingFloatingPointLiteral
{
    double baz = 0e6;
    double baz2 = 0.e6;
    double baz3 = 0.0e6;
    double baz4 = 0.0e-6;
    double baz5 = 0.0e-6;
    double foo = 3.0;
    double barangus2 = .03;
    double barangus = 3.;
    float bazf = 0e6f;
    float baz2f = 0.e6f;
    float baz3f = 0.0e6f;
    float foof = 3.0f;
    float barangus2f = .03f;
    float barangusf = 3.f;

    double good = 0x1.0p10;
    double plusGood = 0x1.0p10d;
    float orDrown = 0x.1p1f;
    double hex = 0xA.Ap0;
    double gm = 3.986_004_418e14; // (m3/s2) gravitational parameter
    double thousand = 1_000.0;
}
