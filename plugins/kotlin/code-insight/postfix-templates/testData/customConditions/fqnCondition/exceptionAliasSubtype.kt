// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:kotlin.Exception
// USE_TOPMOST: false
class MyException : Exception()

fun test(e: MyException) {
    e<caret>
}
