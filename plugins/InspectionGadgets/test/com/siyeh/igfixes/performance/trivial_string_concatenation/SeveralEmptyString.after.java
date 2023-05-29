package com.siyeh.igfixes.performance.trivial_string_concatenation;

class SeveralEmptyString {
    void m(String version) {
        /* hello */
        String s = "t1" +
                   "t2"+
                   "t3";
        String s2 = "";
        // TODO: Applying ModCommand on Document diff invalidates previous warning
        /* hello2 */
        /* hello3 */
        String s3 = "" /* hello1 */ + "t1" /* hello4 */;
        /*hello1*/
        /*hello2*/
        String s4 = "t1";
        String s5 = "<caret>";
        String s6 = "1";
        String s7 = "2";
        String s8 = "3";
    }
}