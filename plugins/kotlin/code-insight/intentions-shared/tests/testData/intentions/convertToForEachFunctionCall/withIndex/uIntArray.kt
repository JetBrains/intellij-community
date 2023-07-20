// WITH_STDLIB
@OptIn(ExperimentalUnsignedTypes::class)
fun test() {
    <caret>for ((index, element) in uintArrayOf(1u).withIndex()) {
        println("$index:$element")
    }
}