// "Create abstract function 'A.Companion.foo'" "false"
// ACTION: Create extension function 'A.Companion.foo'
// ACTION: Create member function 'A.Companion.foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

abstract class A

fun test() {
    val a: Int = A.<caret>foo(2)
}