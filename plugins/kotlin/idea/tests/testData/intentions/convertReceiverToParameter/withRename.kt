// NEW_NAME: myName

/* K1 doesn't rename in tests (isUnitTestMode) */


fun <caret>String.foo(s: String): String {
    return this + s
}