package com.siyeh.igtest.dataflow.constant_value_variable_use;

public class ConstantValueVariableUse {


    public static void test(double number) {
        if (number == -0.0) { // is true for -0.0
            f(number);
        }
        if (number == 1.0) {
            f(<warning descr="Value of 'number' is known to be constant">number</warning>);
        }
    }

    public static void f(double signedZero) {
        System.out.println("signedZero = " + signedZero);
    }

    void m(int i) {
        if ((i) == 2 && true && true) { // polyadic
            f(<warning descr="Value of 'i' is known to be constant">i</warning>);
        }

        int j = 10;
        while (i < 10 && j == 10) {
            f(<warning descr="Value of 'j' is known to be constant">j</warning>);
        }
        for (; i < 10 && j == 10; ) {
            f(<warning descr="Value of 'j' is known to be constant">j</warning>);
        }

        if (i == 3) {
            i = 4;
            System.out.println(i);
        }
    }

}
class C {
    private int hash;
    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) {
            hash = h = "StringUtil.stringHashCode(this, 0, length())".hashCode();
        }
        return h;
    }
}
class D  {
    void f() {
        int positiveValue = 10;
        int counter = 12;
        if (positiveValue == 10) {
            while (counter > 0 && positiveValue > 0) {
                counter--;
                positiveValue--;
            }
        }
    }
}