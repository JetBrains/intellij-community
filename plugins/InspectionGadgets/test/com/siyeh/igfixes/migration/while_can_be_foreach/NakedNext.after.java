package com.siyeh.igfixes.migration.while_can_be_foreach;

import java.util.Iterator;
import java.util.List;

class NakedNext implements Iterable {

    void m(List<String> ss) {
        int count = 0;
        for (String s: ss) {
            count++;
        }
    }
}
