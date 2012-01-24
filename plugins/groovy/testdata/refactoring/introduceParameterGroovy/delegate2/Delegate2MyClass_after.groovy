def foo(def p) {
    foo(p, "foo")
}

def foo(def p, String anObject) {
    print p + anObject
}