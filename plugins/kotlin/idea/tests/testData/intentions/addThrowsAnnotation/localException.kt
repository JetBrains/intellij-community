// WITH_STDLIB
// IS_APPLICABLE: false

fun a() {
    class Ex : RuntimeException()
    <caret>throw Ex()
}