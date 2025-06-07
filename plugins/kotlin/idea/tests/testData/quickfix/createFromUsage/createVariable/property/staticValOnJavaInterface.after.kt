// "Create member property 'J.foo'" "true"
// K2_ACTION: "Create constant field 'foo' in 'J'" "true"
// ERROR: Unresolved reference: foo

fun test() {
    val a: Int = J.foo
}

