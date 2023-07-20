package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  
    boolean m(boolean a, boolean b, boolean c) {
        if (a) {
            <caret>if (b) return true;
            if (c) return true;
            return false;
        }//c1
        return false;//c2
    }
}
