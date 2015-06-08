package com.siyeh.igtest.migration.unnecessary_superfluous_unboxing;




public class UnnecessarySuperfluousUnboxing {


    private void test1(Integer intValue, Long longValue,
                       Byte shortValue, Double doubleValue,
                       Float floatValue, Long byteValue,
                       Boolean booleanValue, Character character) {
        final int bareIntValue = intValue.intValue();
        final long bareLongValue = longValue.longValue();
        final short bareShortValue = shortValue.shortValue();
        final double bareDoubleValue = doubleValue.doubleValue();
        final float bareFloatValue = floatValue.floatValue();
        final byte bareByteValue = byteValue.byteValue();
        final boolean bareBooleanValue = booleanValue.booleanValue();
        final char bareCharValue = character.charValue();
    }

    Integer foo2(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar.intValue();
    }

    Integer foo3(String foo, Integer bar) {
        return foo == null ? 0 : bar.intValue();
    }

    UnnecessarySuperfluousUnboxing(Object object) {}
    UnnecessarySuperfluousUnboxing(long l) {}

    void user(Long l) {
        new UnnecessarySuperfluousUnboxing(l.longValue());
    }

    Integer boxcutter(Integer i) {
      return <warning descr="Unnecessary unboxing 'i.intValue()'">i.intValue()</warning>;
    }
}


class B23 {
    public void set(double value) {}
}
class A23 extends B23 {
    public void set(Object value) {}
    private A23() {
        Object o = 2d;
        set(((Double) o).doubleValue());

        B23 b23 = new B23();
        b23.set(((Double) o).doubleValue());
    }
}