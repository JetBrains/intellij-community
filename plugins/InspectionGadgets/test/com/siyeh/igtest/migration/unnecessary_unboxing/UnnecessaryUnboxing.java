package com.siyeh.igtest.migration.unnecessary_unboxing;




public class UnnecessaryUnboxing {


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

    UnnecessaryUnboxing(Object object) {}
    UnnecessaryUnboxing(long l) {}

    void user(Long l) {
        new UnnecessaryUnboxing(l.longValue());
    }

    void casting(Byte b) {
        System.out.println((byte)b.byteValue());
    }


    byte cast(Integer v) {
       return (byte)v.intValue();
    }
}