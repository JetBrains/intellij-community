// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo
// WITH_STDLIB

class A<T>(val n: T)

fun test() {
    val a: A<Int> = 2.<caret>foo
}
