// "Create member property 'J.foo'" "true"
// K2_ACTION: "Create property 'foo' in 'J'" "true"
// ERROR: Unresolved reference: setFoo
// ERROR: Unresolved reference: a

fun test(j: J) {
    j.set<selection><caret></selection>Foo(a)
}
// IGNORE_K1
