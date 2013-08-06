package com.siyeh.igtest.jdk.auto_boxing;




public class AutoBoxing {

    static {
        Long someNumber = 0L;
        Long aLong = someNumber << 2;
        Long other = someNumber++;
        someNumber = ~someNumber;
        someNumber = -someNumber;
        someNumber = +someNumber;
    }

    public void foo() {
        Integer bar = 3;
        int baz = new Integer(3);
        if (new Integer(3) == 3) {
            return;
        }
        if (new Integer(3) + 3 == 3) {
            return;
        }
        Integer x = 3;
    }


    public void bar(Double value) {
        if (value > 0.0) { // this is not found!
            return;
        }

        bazz(value);
    }

    private void bazz(double value) {
        System.out.println("value = " + value);
        Boolean c = Boolean.TRUE & false;
        Long d = Integer.valueOf(2) & 1L;
    }

    void constantBoxing() {
        Byte s = 8;
        Short j = (byte)7;
    }

    void polyadic() {
        Integer i = 1 + 2 + 3;
    }

    void doInstanceof(Object o) {
        Boolean b = o instanceof String;
    }

    void m(boolean b) {
      System.out.println((Boolean)b);
      final Object o1 = (Object) b;
    }
}
