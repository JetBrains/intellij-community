package com.siyeh.igfixes.performance.trivial_string_concatenation;

class StartComments {
    void m() {
        /* hello3 */
        /* hello4 */
        String s1 = /* hello1 */ "test1" // hello2
                    +
                       "test2";
    }
}