package com.siyeh.ipp.switchtoif.replace_if_with_switch;

import java.math.BigDecimal;

public class Test {
    int test(Object obj, int x) {
        <caret>if (obj instanceof Integer) {
            Integer y = (Integer) obj;
            return ((Integer) obj).byteValue();
        } else if (obj instanceof String && x > 0) {
            return ((String) obj).length();
        } else if (obj instanceof Boolean c) {
            return (Boolean) c ? 42 : 0;
        } else if (obj instanceof Character) {
            return ((BigDecimal) obj).hashCode();
        } else {
            return -1;
        }
    }
}