package com.siyeh.igfixes.performance.trivial_string_concatenation;

class SeveralEmptyString {
    void m(String version) {
        String s = "t1" +
                   "" +
          /* hello */
                   "" +
                   "t2"+
                   "" +
                   "t3" +
                   "";
        String s2 = "" + "";
        String s3 = "" /* hello1 */ + /* hello2 */ "" /* hello3 */  + "t1" + "" /* hello4 */;
        String s4 = "" /*hello1*/ + /*hello2*/ "t1";
        String s5 = "<caret>" + "" + "";
        String s6 = "1" + "" + "";
        String s7 = "" + "2" + "";
        String s8 = "" + "" + "3";
    }
}