package com.siyeh.igtest.bugs.constant_assert_condition;




public class ConstantAssertCondtion {

    void foo() {
        assert true;
    }

    void bar(int i) {
        assert i == 10;
    }
}