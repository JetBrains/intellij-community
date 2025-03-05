// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual class My<caret> actual constructor(val a: Int) {
    fun foo(param: String) = param.length
    actual val text: String = "test"
    constructor(): this(42)
}