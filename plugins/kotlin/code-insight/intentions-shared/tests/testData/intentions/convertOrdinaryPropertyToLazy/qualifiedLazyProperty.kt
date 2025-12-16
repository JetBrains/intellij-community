// IS_APPLICABLE: false
// IGNORE_K1
private val lazyString: Lazy<String> = lazy { "hello" }


class B {
    private val x =<caret> lazyString
}