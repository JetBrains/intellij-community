class C {
    void f(char[] chars) {
        for (int i = chars.length - 1; i >= 0 && chars[i] <= ' '; <caret>i--) {
        }
    }
}