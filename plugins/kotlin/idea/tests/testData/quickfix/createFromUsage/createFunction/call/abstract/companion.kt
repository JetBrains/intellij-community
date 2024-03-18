// "Create abstract function 'A.Companion.foo'" "false"
// ERROR: Unresolved reference: foo

abstract class A

fun test() {
    val a: Int = A.<caret>foo(2)
}