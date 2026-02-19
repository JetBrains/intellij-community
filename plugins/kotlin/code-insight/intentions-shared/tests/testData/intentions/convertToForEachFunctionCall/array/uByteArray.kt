// WITH_STDLIB
@OptIn(ExperimentalUnsignedTypes::class)
fun foo() {
    <caret>for (x in ubyteArrayOf(1u)) println(x)
}