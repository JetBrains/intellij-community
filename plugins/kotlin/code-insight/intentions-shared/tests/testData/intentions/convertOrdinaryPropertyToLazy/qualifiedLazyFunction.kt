// IS_APPLICABLE: false
// IGNORE_K1
class A {
    fun computeLazily(): Lazy<String> = lazy { "hello" }
}

class B {
    private val x =<caret> A().computeLazily()
}