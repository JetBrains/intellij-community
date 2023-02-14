class D {
    void f(final String aktualnaMena, final boolean tisice) {
        final boolean sss = <caret>aktualnaMena == null || aktualnaMena.equals("SSS") ? tisice : false;
    }
}