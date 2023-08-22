// WITH_STDLIB
fun List<Int>?.foo() {
    fun List<Int>?.bar() {
        if (<caret>this == null || this@bar.isEmpty()) println(0) else println(size)
    }
}
