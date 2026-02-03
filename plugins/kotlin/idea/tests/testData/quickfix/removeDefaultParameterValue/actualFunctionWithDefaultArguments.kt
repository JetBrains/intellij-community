// "Remove default parameter value" "true"
// DISABLE_ERRORS
interface Foo {
    fun test(x: Int, y: Int)
}

expect class Bar : Foo {
    override fun test(x: Int, y: Int)
}

actual class Bar : Foo {
    actual override fun test(x: Int, y: Int = 1<caret>) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveDefaultParameterValueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveDefaultParameterValueFix