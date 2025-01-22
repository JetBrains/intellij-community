// "Create expected class in common module testModule_Common" "true"
// DISABLE_ERRORS
// IGNORE_K2

actual class My<caret>(val a: Int) {
    fun foo(param: String) = param.length

    actual constructor(): this(42)
}