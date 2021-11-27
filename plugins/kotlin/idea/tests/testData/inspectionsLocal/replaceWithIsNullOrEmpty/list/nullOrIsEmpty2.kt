// WITH_STDLIB
fun List<Int>?.test() {
    if (<caret>this == null || isEmpty()) println(0) else println(size)
}
