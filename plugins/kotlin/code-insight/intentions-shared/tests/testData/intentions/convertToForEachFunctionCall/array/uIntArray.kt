// WITH_STDLIB
@OptIn(ExperimentalUnsignedTypes::class)
fun foo() {
    <caret>for (x in uintArrayOf(1u)) println(x)
}