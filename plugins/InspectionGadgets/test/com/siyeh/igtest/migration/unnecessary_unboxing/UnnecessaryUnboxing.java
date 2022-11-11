package com.siyeh.igtest.migration.unnecessary_unboxing;




public class UnnecessaryUnboxing {


    private void test1(Integer intValue, Long longValue,
                       Byte shortValue, Double doubleValue,
                       Float floatValue, Long byteValue,
                       Boolean booleanValue, Character character) {
        final int bareIntValue = intValue.<warning descr="Unnecessary unboxing">intValue</warning>();
        final long bareLongValue = longValue.<warning descr="Unnecessary unboxing">longValue</warning>();
        final short bareShortValue = shortValue.shortValue();
        final double bareDoubleValue = doubleValue.<warning descr="Unnecessary unboxing">doubleValue</warning>();
        final float bareFloatValue = floatValue.<warning descr="Unnecessary unboxing">floatValue</warning>();
        final byte bareByteValue = byteValue.byteValue();
        final boolean bareBooleanValue = booleanValue.<warning descr="Unnecessary unboxing">booleanValue</warning>();
        final char bareCharValue = character.<warning descr="Unnecessary unboxing">charValue</warning>();
    }

    Integer foo2(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar.intValue();
    }

    Integer foo3(String foo, Integer bar) {
        return foo == null ? 0 : bar.<warning descr="Unnecessary unboxing">intValue</warning>();
    }

    UnnecessaryUnboxing(Object object) {}
    UnnecessaryUnboxing(long l) {}

    void user(Long l) {
        new UnnecessaryUnboxing(l.longValue());
    }

    void casting(Byte b) {
        System.out.println((byte)b.<warning descr="Unnecessary unboxing">byteValue</warning>());
        casting((((b.<warning descr="Unnecessary unboxing">byteValue</warning>()))));
    }


    byte cast(Integer v) {
       return (byte)v.intValue();
    }

    void comparison() {
        Integer a = Integer.valueOf(1024);
        Integer b = Integer.valueOf(1024);
        System.out.println(a == b == true); // false
        System.out.println(a.intValue() == b.intValue() == true); // true
        System.out.println(a.<warning descr="Unnecessary unboxing">intValue</warning>() == 1024);
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
        b23.set(((Double) o).<warning descr="Unnecessary unboxing">doubleValue</warning>());
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