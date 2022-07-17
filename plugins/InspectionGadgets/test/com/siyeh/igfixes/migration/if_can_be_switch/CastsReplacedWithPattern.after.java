package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
    int test(Object obj, int x) {
        <caret>return switch (obj) {
            case Integer y -> y.byteValue();
            case String s && x > 0 -> s.length();
            case Boolean c -> (Boolean) c ? 42 : 0;
            case Character character -> ((BigDecimal) obj).hashCode();
            case null, default -> -1;
        };
    }
}