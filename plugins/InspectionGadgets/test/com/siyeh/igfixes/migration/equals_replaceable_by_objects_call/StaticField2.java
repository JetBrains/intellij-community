class T {
    static class A {
        static String a;
    }
    static boolean dfferent(String s) {
        return A.a != s && (A.a == null || !A.a.<caret>equals(s));
    }
}