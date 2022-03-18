// WITH_STDLIB
fun List<Int>?.test() {
    if (<caret>this@test == null || this@test.isEmpty()) println(0) else println(size)
}
