// "Create member property 'A.Companion.foo'" "true"
// K2_ACTION: "Create property 'foo'" "true"
// ERROR: Unresolved reference: foo

fun test() {
    val a: Int = A.foo
}