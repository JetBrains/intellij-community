class A<T>

fun A<String>.fooString() {}
fun A<Int>.fooInt() {}
fun <T> A<T>.foo() {}

typealias AInt = A<Int>

fun main() {
    AInt::<caret>
}

// EXIST: { "itemText": "foo" }
// EXIST: { "itemText": "fooInt" }
// ABSENT: { "itemText": "fooString" }