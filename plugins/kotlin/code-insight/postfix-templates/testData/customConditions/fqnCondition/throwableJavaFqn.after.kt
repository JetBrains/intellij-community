// TEMPLATE_TEXT: println($EXPR$)
// CONDITION: kotlin.fqn:java.lang.Throwable
// USE_TOPMOST: false
fun test(t: Throwable) {
    println(t)
}
