package com.siyeh.igtest.migration.unnecessary_superfluous_boxing;




public class UnnecessarySuperfluousBoxing {

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
        return foo == null ? <warning descr="Unnecessary boxing 'Integer.valueOf(0)'">Integer.valueOf(0)</warning> : bar;
    }

    void noUnboxing(Object val) {
        if (val == Integer.valueOf(0)) {

        } else if (Integer.valueOf(1) == val) {}
        boolean b = true;
        Boolean.valueOf(b).toString();
    }

    public Integer getBar() {
        return null;
    }

    void doItNow(UnnecessarySuperfluousBoxing foo) {
        Integer bla = foo == null ? Integer.valueOf(0) : foo.getBar();
    }

    private int i;

    private String s;

    public <T>T get(Class<T> type) {
        if (type == Integer.class) {
            return (T) new Integer(i);
        } else if (type == String.class) {
            return (T) s;
        }
        return null;
    }

    int bababoxing(int i) {
      return <warning descr="Unnecessary boxing 'Integer.valueOf(i)'">Integer.valueOf(i)</warning>;
    }
}