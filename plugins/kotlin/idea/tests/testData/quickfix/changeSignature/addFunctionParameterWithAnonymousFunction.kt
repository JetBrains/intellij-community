// "Add parameter to function 'baz'" "true"
fun baz() {}

fun foo() {
    baz(fun(i: Int): String { return i.toString() }<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix