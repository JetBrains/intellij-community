// SHOULD_FAIL_WITH: Callable reference '::foo' cannot be updated: its receiver value cannot be turned into an argument

fun <caret>String.foo(n: Int): Boolean = true

fun String.test() {
    val ref: (Int) -> Boolean = ::foo
}