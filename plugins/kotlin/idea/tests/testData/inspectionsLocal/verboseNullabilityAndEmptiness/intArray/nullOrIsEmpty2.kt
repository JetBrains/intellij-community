// WITH_STDLIB
// PROBLEM: none
fun IntArray?.test() {
    if (<caret>this == null || isEmpty()) println(0) else println(size)
}
