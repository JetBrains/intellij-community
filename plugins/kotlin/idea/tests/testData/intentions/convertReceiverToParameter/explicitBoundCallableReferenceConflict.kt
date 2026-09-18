// SHOULD_FAIL_WITH: Callable reference 's::foo' cannot be updated: its receiver value cannot be turned into an argument

fun <caret>String.foo(n: Int): Boolean = true

fun test(s: String) {
    val ref: (Int) -> Boolean = s::foo
}