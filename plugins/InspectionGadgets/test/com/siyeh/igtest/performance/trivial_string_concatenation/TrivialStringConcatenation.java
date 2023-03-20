package com.siyeh.igtest.performance.trivial_string_concatenation;

public class TrivialStringConcatenation {

    public void foo() {
        final String foo = "" + 4 + "" + 3;
        String bar = <warning descr="Empty string used in concatenation">""</warning> + new Integer(4) + "asdf";
        Float aFloat = new Float(3.0);
        String baz = <warning descr="Empty string used in concatenation">""</warning> + aFloat;

        String trivial = <warning descr="Empty string used in concatenation">""</warning> + " ";
        String doubleConstant = "foo" + (<warning descr="Empty string used in concatenation">""</warning>) + (<warning descr="Empty string used in concatenation">""</warning>);
    }
}
