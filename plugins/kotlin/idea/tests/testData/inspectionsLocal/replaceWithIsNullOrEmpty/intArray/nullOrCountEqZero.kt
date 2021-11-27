// WITH_STDLIB
// PROBLEM: none
fun IntArray?.test() {
    if (<caret>this == null || count() == 0) println(0) else println(size)
}
