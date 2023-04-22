// WITH_STDLIB
@OptIn(ExperimentalUnsignedTypes::class)
fun foo() {
    <caret>for (x in ushortArrayOf(1u)) println(x)
}