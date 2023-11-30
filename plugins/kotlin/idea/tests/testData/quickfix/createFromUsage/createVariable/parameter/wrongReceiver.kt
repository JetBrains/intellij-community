// "Create parameter 'value'" "true"
// DISABLE-ERRORS
interface Tr {
    fun foo(value: String, b: String) = ""
    fun bar() = foo(<caret>value, b)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.createFromUsage.createVariable.CreateParameterFromUsageFix