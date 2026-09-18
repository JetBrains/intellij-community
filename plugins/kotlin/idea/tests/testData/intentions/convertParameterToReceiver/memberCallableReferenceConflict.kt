// SHOULD_FAIL_WITH: Callable reference '::foo' cannot be updated to a member with an extension receiver

class A {
    fun foo(<caret>s: String) {}

    fun test() {
        val ref: (String) -> Unit = ::foo
    }
}