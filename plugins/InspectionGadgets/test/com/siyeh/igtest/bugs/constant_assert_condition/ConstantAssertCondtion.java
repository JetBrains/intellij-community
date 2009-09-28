package com.siyeh.igtest.bugs.constant_assert_condition;




public class ConstantAssertCondtion {

    void foo() {
        assert true;
    }

    void bar(int i) {
        assert i == 10;
    }

    void noWarn() {
        try {
            
        } catch (RuntimeException e) {
            // should never happen
            assert (false);
        }
    }
}