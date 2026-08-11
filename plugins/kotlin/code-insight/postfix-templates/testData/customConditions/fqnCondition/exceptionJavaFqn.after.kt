// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:java.lang.Exception
// USE_TOPMOST: false
fun test(e: Exception) {
    println(e)
}
