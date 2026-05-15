// IDEA-384408
fun main() {
    val a: A? = A(56)

    <caret>println(a)
}

class A(val num: Int)

// INVOCATION_COUNT: 1
// ELEMENT: num
// RUNTIME_TYPE: A
// AUTOCOMPLETE_SETTING: true
