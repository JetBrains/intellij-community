class C {
    void m(String arg) {
        if (arg != null && <caret>arg instanceof String) {
            System.out.println(arg);
        }
    }
}
