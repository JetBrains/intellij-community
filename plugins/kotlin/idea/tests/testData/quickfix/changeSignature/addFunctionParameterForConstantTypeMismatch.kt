// "Add 1st parameter to function 'foo'" "true"

fun foo(name: String) = Unit

fun test() {
    val foo = foo(<caret>1, "name")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix