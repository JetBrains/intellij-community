// WITH_STDLIB
@OptIn(ExperimentalUnsignedTypes::class)
fun foo() {
    <caret>for (x in ulongArrayOf(1u)) println(x)
}