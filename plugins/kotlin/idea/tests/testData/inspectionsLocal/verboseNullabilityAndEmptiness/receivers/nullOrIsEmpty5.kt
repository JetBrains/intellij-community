// WITH_STDLIB
// PROBLEM: none

// ERROR: Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type List<Int>?

fun List<Int>?.foo() {
    fun List<Int>?.bar() {
        if (<caret>this@foo == null || this@bar.isEmpty()) println(0) else println(size)
    }
}
