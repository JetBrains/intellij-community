package pkg;

public class TestConstantsAsIs {
    static final boolean T = true;
    static final boolean F = false;

    static final char C0 = '\n';
    static final char C1 = 'a';
    static final char C2 = 512;

    static final byte BMin = Byte.MIN_VALUE;
    static final byte BMax = Byte.MAX_VALUE;

    static final short SMin = Short.MIN_VALUE;
    static final short SMax = Short.MAX_VALUE;

    static final int IMin = Integer.MIN_VALUE;
    static final int IMax = Integer.MAX_VALUE;

    static final long LMin = Long.MIN_VALUE;
    static final long LMax = Long.MAX_VALUE;

    static final float FNan = Float.NaN;
    static final float FNeg = Float.NEGATIVE_INFINITY;
    static final float FPos = Float.POSITIVE_INFINITY;
    static final float FMin = Float.MIN_VALUE;
    static final float FMax = Float.MAX_VALUE;

    static final double DNan = Double.NaN;
    static final double DNeg = Double.NEGATIVE_INFINITY;
    static final double DPos = Double.POSITIVE_INFINITY;
    static final double DMin = Double.MIN_VALUE;
    static final double DMax = Double.MAX_VALUE;

    static @interface A {
        Class<?> value();
    }

    @A(byte.class) void m1() { }
    @A(char.class) void m2() { }
    @A(double.class) void m3() { }
    @A(float.class) void m4() { }
    @A(int.class) void m5() { }
    @A(long.class) void m6() { }
    @A(short.class) void m7() { }
    @A(boolean.class) void m8() { }
    @A(void.class) void m9() { }
    @A(java.util.Date.class) void m10() { }
}