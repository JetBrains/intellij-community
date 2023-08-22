// "Add parameter to function 'foo'" "true"
// DISABLE-ERRORS
fun foo() {}

class Test {
    val x: String = ""
        get() {
            foo(field<caret>)
            return field
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix