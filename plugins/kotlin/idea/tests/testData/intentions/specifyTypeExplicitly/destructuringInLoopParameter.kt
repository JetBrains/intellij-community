// IS_APPLICABLE: false
// WITH_STDLIB
fun test(array: IntArray) {
    for ((index, value)<caret> in array.withIndex()) {
        println("the element at $index is $value")
    }
}