class C {
    C(int i) {}

    C(Object[] os) {}
}

new <caret>C(new Integer(0))