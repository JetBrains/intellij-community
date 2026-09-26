// "Create abstract function 'A.Companion.foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

abstract class A

fun test() {
    val a: Int = A.<caret>foo(2)
}