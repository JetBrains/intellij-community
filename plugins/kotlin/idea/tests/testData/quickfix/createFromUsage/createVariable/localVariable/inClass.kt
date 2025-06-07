// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

class A {
    val t: Int = <caret>foo
}