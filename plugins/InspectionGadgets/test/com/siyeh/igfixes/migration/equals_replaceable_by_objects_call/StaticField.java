class T {
    static class A {
        static String a;
    }
    static boolean same(String s) {
        return A.a != null && A.a.<caret>equals(s);
    }
}