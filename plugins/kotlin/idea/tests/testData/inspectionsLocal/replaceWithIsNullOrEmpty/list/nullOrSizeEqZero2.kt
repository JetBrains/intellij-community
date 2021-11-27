// WITH_STDLIB
fun List<Int>?.test() {
    if (<caret>this == null || size == 0) println(0) else println(size)
}
