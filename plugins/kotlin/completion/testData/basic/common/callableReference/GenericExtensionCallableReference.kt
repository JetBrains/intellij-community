class A<T>

fun A<String>.fooString() {}
fun A<Int>.fooInt() {}
fun <T> A<T>.foo() {}


fun main() {
    A<Int>::<caret>
}

// EXIST: { "itemText": "foo" }
// EXIST: { "itemText": "fooInt" }
// ABSENT: { "itemText": "fooString" }