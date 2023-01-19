package com.siyeh.igfixes.performance.trivial_string_concatenation;

class StartComments {
    void m() {
        /* hello5 */
        String s1 = /* hello1 */ "test1" /* hello2 */ +
          /* hello3 */ // hello4
                "test2"<caret> /* hello6 */ ;
    }
}