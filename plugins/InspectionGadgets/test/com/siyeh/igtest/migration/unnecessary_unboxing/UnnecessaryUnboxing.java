package com.siyeh.igtest.migration.unnecessary_unboxing;




public class UnnecessaryUnboxing {


    private void test1(Integer intValue, Long longValue,
                       Byte shortValue, Double doubleValue,
                       Float floatValue, Long byteValue,
                       Boolean booleanValue, Character character) {
        final int bareIntValue = <warning descr="Unnecessary unboxing 'intValue.intValue()'">intValue.intValue()</warning>;
        final long bareLongValue = <warning descr="Unnecessary unboxing 'longValue.longValue()'">longValue.longValue()</warning>;
        final short bareShortValue = shortValue.shortValue();
        final double bareDoubleValue = <warning descr="Unnecessary unboxing 'doubleValue.doubleValue()'">doubleValue.doubleValue()</warning>;
        final float bareFloatValue = <warning descr="Unnecessary unboxing 'floatValue.floatValue()'">floatValue.floatValue()</warning>;
        final byte bareByteValue = byteValue.byteValue();
        final boolean bareBooleanValue = <warning descr="Unnecessary unboxing 'booleanValue.booleanValue()'">booleanValue.booleanValue()</warning>;
        final char bareCharValue = <warning descr="Unnecessary unboxing 'character.charValue()'">character.charValue()</warning>;
    }

    Integer foo2(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar.intValue();
    }

    Integer foo3(String foo, Integer bar) {
        return foo == null ? 0 : <warning descr="Unnecessary unboxing 'bar.intValue()'">bar.intValue()</warning>;
    }

    UnnecessaryUnboxing(Object object) {}
    UnnecessaryUnboxing(long l) {}

    void user(Long l) {
        new UnnecessaryUnboxing(l.longValue());
    }

    void casting(Byte b) {
        System.out.println((byte)<warning descr="Unnecessary unboxing 'b.byteValue()'">b.byteValue()</warning>);
        casting((((<warning descr="Unnecessary unboxing 'b.byteValue()'">b.byteValue()</warning>))));
    }


    byte cast(Integer v) {
       return (byte)v.intValue();
    }

    void comparison() {
        Integer a = Integer.valueOf(1024);
        Integer b = Integer.valueOf(1024);
        System.out.println(a == b == true); // false
        System.out.println(a.intValue() == b.intValue() == true); // true
        System.out.println(<warning descr="Unnecessary unboxing 'a.intValue()'">a.intValue()</warning> == 1024);
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
        b23.set(<warning descr="Unnecessary unboxing '((Double) o).doubleValue()'">((Double) o).doubleValue()</warning>);
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