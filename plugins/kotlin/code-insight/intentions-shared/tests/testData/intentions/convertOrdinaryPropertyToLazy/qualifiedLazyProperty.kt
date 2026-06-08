// IS_APPLICABLE: false

private val lazyString: Lazy<String> = lazy { "hello" }


class B {
    private val x =<caret> lazyString
}