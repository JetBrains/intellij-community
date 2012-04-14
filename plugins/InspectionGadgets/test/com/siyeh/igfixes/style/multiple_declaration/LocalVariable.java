package com.siyeh.igfixes.style.multiple_declaration;

public class SimpleStringBuffer {
    String foo() {
        int i = 0, j<caret> = 0;
    }
}
