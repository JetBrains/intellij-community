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
    }

}