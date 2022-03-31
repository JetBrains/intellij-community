package com.siyeh.igtest.migration.unnecessary_superfluous_unboxing;




public class UnnecessarySuperfluousUnboxing {


    private void test1(Integer intObject, Long longObject,
                       Byte shortObject, Double doubleObject,
                       Float floatObject, Long byteObject,
                       Boolean booleanObject, Character character) {
        final int bareIntValue = intObject.intValue();
        final long bareLongValue = longObject.longValue();
        final short bareShortValue = shortObject.shortValue();
        final double bareDoubleValue = doubleObject.doubleValue();
        final float bareFloatValue = floatObject.floatValue();
        final byte bareByteValue = byteObject.byteValue();
        final boolean bareBooleanValue = booleanObject.booleanValue();
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
      return i<warning descr="Unnecessary unboxing 'i'">.intValue()</warning>;
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