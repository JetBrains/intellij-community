package com.siyeh.igtest.migration.unnecessary_unboxing;




public class UnnecessaryUnboxing {


    private void test1(Integer intWrapper, Long longWrapper,
                       Byte shortWrapper, Double doubleWrapper,
                       Float floatWrapper, Long byteWrapper,
                       Boolean booleanWrapper, Character character) {
        final int bareIntValue = intWrapper<warning descr="Unnecessary unboxing 'intWrapper'">.intValue()</warning>;
        final long bareLongValue = longWrapper<warning descr="Unnecessary unboxing 'longWrapper'">.longValue()</warning>;
        final short bareShortValue = shortWrapper.shortValue();
        final double bareDoubleValue = doubleWrapper<warning descr="Unnecessary unboxing 'doubleWrapper'">.doubleValue()</warning>;
        final float bareFloatValue = floatWrapper<warning descr="Unnecessary unboxing 'floatWrapper'">.floatValue()</warning>;
        final byte bareByteValue = byteWrapper.byteValue();
        final boolean bareBooleanValue = booleanWrapper<warning descr="Unnecessary unboxing 'booleanWrapper'">.booleanValue()</warning>;
        final char bareCharValue = character<warning descr="Unnecessary unboxing 'character'">.charValue()</warning>;
    }

    Integer foo2(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar.intValue();
    }

    Integer foo3(String foo, Integer bar) {
        return foo == null ? 0 : bar<warning descr="Unnecessary unboxing 'bar'">.intValue()</warning>;
    }

    UnnecessaryUnboxing(Object object) {}
    UnnecessaryUnboxing(long l) {}

    void user(Long l) {
        new UnnecessaryUnboxing(l.longValue());
    }

    void casting(Byte b) {
        System.out.println((byte)b<warning descr="Unnecessary unboxing 'b'">.byteValue()</warning>);
        casting((((b<warning descr="Unnecessary unboxing 'b'">.byteValue()</warning>))));
    }


    byte cast(Integer v) {
       return (byte)v.intValue();
    }

    void comparison() {
        Integer a = Integer.valueOf(1024);
        Integer b = Integer.valueOf(1024);
        System.out.println(a == b == true); // false
        System.out.println(a.intValue() == b.intValue() == true); // true
        System.out.println(a<warning descr="Unnecessary unboxing 'a'">.intValue()</warning> == 1024);
    }
}


class B23 {
    public void set(double value) {}
}
class A23 extends B23 {
    public void set(Object value) {}
    private A23() {
        Object o = 2d;
        B23 b23 = new B23();
        b23.set(((Double) o)<warning descr="Unnecessary unboxing '((Double) o)'">.doubleValue()</warning>);
    }
}
class test {
    static abstract class AAbstractLongMap extends java.util.AbstractMap<Long, Long> {
        public Long put(long key, Long value) {
            return null;
        }
    }
    static AAbstractLongMap new_times;
    void m(Long l) {
        new_times.put(l.longValue(), new Long(2l));
    }
}

class PassedToUnfriendlyLambdaOverloadedMethod {
    interface GetInt { int get(); }
    interface GetInteger { Integer get(); }

    private void m(GetInt getter) {
        System.out.println(getter);
    }

    private void m(GetInteger getter) {
        System.out.println(getter);
    }

    void test(boolean cond) {
        m(() -> {
            if (cond)
                return 42;
            else
                return new Integer(42).intValue();
        });
        m(() -> cond ? new Integer(42).intValue() : foo());
        m(() -> new Integer(42).intValue());
        m(() -> new Integer(42));
    }

    private <T> T foo() {
        return null;
    }
}