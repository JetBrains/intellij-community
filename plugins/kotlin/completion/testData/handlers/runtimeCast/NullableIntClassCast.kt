// IDEA-384408
fun main() {
    val a: Int? = 123

    <caret>println(a)
}

// INVOCATION_COUNT: 1
// ELEMENT: plus
// TAIL_TEXT: "(other: Int)"
// RUNTIME_TYPE: java.lang.Integer
// AUTOCOMPLETE_SETTING: true
