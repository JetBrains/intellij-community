// WITH_STDLIB
fun test(foo: String): String {
    return <caret>"$foo.${"a".repeat(5)}.${"b".filter { it.isLetter() }}"
}
