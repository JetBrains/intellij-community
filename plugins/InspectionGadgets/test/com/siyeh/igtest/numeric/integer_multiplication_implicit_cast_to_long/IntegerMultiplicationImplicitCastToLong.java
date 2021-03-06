package com.siyeh.igtest.numeric.integer_multiplication_implicit_cast_to_long;

public class IntegerMultiplicationImplicitCastToLong {
    void rightArgOfShift(int step) {
        // Shift operations are not subjected to binary promotion (JLS 15.19), operands are promoted separately using unary promotion rules
        long l = 1L << (step * 8);
    }

    void leftArgOfShift(int i) {
        long l = (<warning descr="i * i: integer multiplication implicitly cast to long">i * i</warning>) << 8;
    }
    
    void testConcat(int step) {
        String res = "Result: "+(1L + <warning descr="1000*step: integer multiplication implicitly cast to long">1000*step</warning>);
    }

    void reportOnlyInnerMultInMultOrShift(int i) {
        long l = (i * (<warning descr="i * i: integer multiplication implicitly cast to long">i * i</warning>));
        long l1 = (<warning descr="i * i: integer multiplication implicitly cast to long">i * i</warning>) << 1;
    }
    
    public void foo() {
        int x = 65336;
        final long val = <warning descr="65336 * x: integer multiplication implicitly cast to long">65336 * x</warning>;
        long other = <warning descr="Integer.valueOf(65336) * Integer.valueOf(x): integer multiplication implicitly cast to long">Integer.valueOf(65336) * Integer.valueOf(x)</warning>;
        long third = <warning descr="x << 24: integer shift implicitly cast to long">x << 24</warning>;
        long polyadic = <warning descr="x * 1024 * 1024: integer multiplication implicitly cast to long">x * 1024 * 1024</warning>;
        long incomplete = x * x *<error descr="Expression expected">;</error>
    }
    
    void overflow(int j, int k) {
        for(int i=0; i<10; i++) {
            long l = i * 2;
        }
        long l1 = <warning descr="j * 2: integer multiplication implicitly cast to long">j * 2</warning>;
        if (j >= 0 && j < 100) {
            long l2 = j * 2;
            long l3 = <warning descr="j * k: integer multiplication implicitly cast to long">j * k</warning>;
            if (k >= 0 && k < 100) {
                long l4 = j * k;
            }
        }
        long l5 = -1 * j; // do not consider -1*j as an overflow to avoid too much noise (after all we don't warn about long x = -intVal;)
    }
    
    void overflowShift(byte b1, byte b2, byte b3, byte b4) {
        long value = (b1 << 24) | (b2 << 16) | (b3 << 8) | (b4 << 0);
        long value2 = <warning descr="b1 << 25: integer shift implicitly cast to long">b1 << 25</warning>;
    }

    private static final int OFFSET1 = 24;
    private static final int OFFSET2 = 16;
    private static final int OFFSET3 = 8;

    void overflowShiftArray(byte[] bytes) {
        long value = (bytes[0] << OFFSET1) & 0xFF000000;
        value += (bytes[1] << OFFSET2) & 0xFF0000;
        value += (bytes[2] << OFFSET3) & 0xFF00;
        System.out.println(value);
    }
    
    void overflowShiftMask(int val) {
        long l1 = (val & 255) << 16;
        long l2 = (val & 255) << 8;
    }
    
    int explicitCast(int a, long b) {
        return (int)(a * 300 + b); 
    }

    long longCastAfterOverflow(int a, int b, int c) {
        return (long) (<warning descr="a * b * c: integer multiplication implicitly cast to long">a * b * c</warning>);
    }

    // IDEA-229673
    public void foo(boolean[] rgsToRead, int elements)
    {
        for (int i = 0, valOffset = 0; i < elements; ++i, valOffset += 64) {
            long val = 0;
            for (int j = 0; j < 64; ++j) {
                int ix = valOffset + j;
                if (rgsToRead.length == ix) break;
                if (!rgsToRead[ix]) continue;
                val = val | (<warning descr="1 << j: integer shift implicitly cast to long">1 << j</warning>);
            }
        }
    }
    
    void testSquare(int val, int square) {
        org.junit.Assert.assertEquals(val * val, square);
    }
}
