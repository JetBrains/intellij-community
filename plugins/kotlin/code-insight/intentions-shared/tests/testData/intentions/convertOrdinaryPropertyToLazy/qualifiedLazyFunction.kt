// IS_APPLICABLE: false

class A {
    fun computeLazily(): Lazy<String> = lazy { "hello" }
}

class B {
    private val x =<caret> A().computeLazily()
}