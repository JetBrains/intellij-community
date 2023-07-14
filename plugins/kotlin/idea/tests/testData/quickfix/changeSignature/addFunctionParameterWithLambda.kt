// "Add parameter to function 'baz'" "true"
fun baz() {}

fun foo() {
    baz { i: Int -> i.toString() }<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix