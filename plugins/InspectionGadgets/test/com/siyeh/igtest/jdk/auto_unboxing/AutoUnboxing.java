package com.siyeh.igtest.jdk.auto_unboxing;



public class AutoUnboxing {
    {
        Long someNumber = Long.valueOf(0);
        someNumber++;
        long l = someNumber + 0;
        Long aLong = Long.valueOf(someNumber << 2);
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
        if (value > 0.0) {
            return;
        }

        bazz(value);
    }

    private void bazz(double value) {
        System.out.println("value = " + value);
        Boolean c = Boolean.TRUE & false;
        Long d = Integer.valueOf(2) & 1L;
    }

    private boolean noWarn(Integer i1, Integer i2) {
      return i1 == i2;
    }

    void m(Boolean b) {
        assert b;
    }

    void n(Integer i) {
      switch(i) {
        case 1: break;
        case 2: break;
        case 3: break;
        default:
      }
    }

    void m(Object o, Boolean b, Number n) {
        if ((boolean) o) {}
        if ((boolean) b) {}
        if ((int)n) {}
    }
}
