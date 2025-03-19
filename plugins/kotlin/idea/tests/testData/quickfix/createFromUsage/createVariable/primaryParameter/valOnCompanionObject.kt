// "Create member property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo

class A<T>(val n: T) {
    companion object {

    }
}

fun test() {
    val a: Int = A.<caret>foo
}
