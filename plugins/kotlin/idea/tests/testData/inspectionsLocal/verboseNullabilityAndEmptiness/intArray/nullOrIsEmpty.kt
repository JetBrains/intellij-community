// WITH_STDLIB
// PROBLEM: none
fun test(intArray: IntArray?) {
    if (<caret>intArray == null || intArray.isEmpty()) println(0) else println(intArray.size)
}
