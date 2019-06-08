package com.siyeh.igfixes.style.multiple_declaration;

public class SimpleStringBuffer {
    String foo() {
        int i<caret> = 0, j = 0;
    }
}
