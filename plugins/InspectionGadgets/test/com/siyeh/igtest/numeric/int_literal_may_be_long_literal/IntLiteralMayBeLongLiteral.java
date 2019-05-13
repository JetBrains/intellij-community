package com.siyeh.igtest.numeric.int_literal_may_be_long;

public class IntLiteralMayBeLongLiteral {

    void foo() {
        System.out.println(<warning descr="'(long) 1' could be replaced with '1L'">(long) 1</warning>);
        System.out.println(<warning descr="'(long) -/*yes, minus*/1' could be replaced with '-1L'">(long) -/*yes, minus*/1</warning>);
        System.out.println(<warning descr="'(long)-(-(6))' could be replaced with '-(-(6L))'">(long)-(-(6))</warning>);
    }

    void error() {
        <error descr="Incompatible types. Found: 'long', required: 'int'">int answer = (long)42;</error>
    }
}
