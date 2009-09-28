package com.siyeh.igtest.jdk15.unnecessary_boxing;




public class UnnecessaryBoxing {

    Integer foo(String foo, Integer bar) {
        return foo == null ? Integer.valueOf(0) : bar;
    }

    public static void main(String[] args)
    {
        final Integer intValue = new Integer(3);
        final Long longValue = new Long(3L);
        final Long longValue2 = new Long(3);
        final Short shortValue = new Short((short)3);
        final Double doubleValue = new Double(3.0);
        final Float floatValue = new Float(3.0F);
        final Byte byteValue = new Byte((byte)3);
        final Boolean booleanValue = new Boolean(true);
        final Character character = new Character('c');
    }

    Integer foo2(String foo, int bar) {
        return foo == null ? Integer.valueOf(0) : bar;
    }

}