// "Create member property 'A.foo'" "true"
// K2_ACTION: "Create field 'foo' in 'A'" "true"
// ERROR: Unresolved reference: foo

fun test(): String? {
    return A().foo
}