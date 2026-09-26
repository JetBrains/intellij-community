// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo
}
