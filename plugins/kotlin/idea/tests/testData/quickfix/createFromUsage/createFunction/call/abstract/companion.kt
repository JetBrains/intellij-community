// "Create abstract function 'A.Companion.foo'" "false"
// ACTION: Create extension function 'A.Companion.foo'
// ACTION: Create member function 'A.Companion.foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

abstract class A

fun test() {
    val a: Int = A.<caret>foo(2)
}