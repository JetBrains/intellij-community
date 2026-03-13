class A<T> {
    inner class B<R>
}

fun A<String>.B<Int>.fooStringInt() {}
fun <T, R> A<T>.B<R>.foo() {}
fun <R> A<Int>.B<R>.fooAInt() {}
fun <T> A<T>.B<String>.fooBString() {}

fun main() {
    A<Int>.B<String>::<caret>
}

// EXIST: { "itemText": "foo" }
// EXIST: { "itemText": "fooBString" }
// EXIST: { "itemText": "fooAInt"  }
// ABSENT: { "itemText": "fooStringInt" }
