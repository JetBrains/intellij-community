package com.siyeh.ipp.trivialif.convert_to_nested_if;

public class X {
  
    boolean m(boolean a, boolean b, boolean c) {
        //c1
        if (a) {
            if (b) r<caret>eturn true;
            if (c) return true;
            return false;
        }
        return false;//c2
    }
}
