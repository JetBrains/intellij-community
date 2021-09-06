// WITH_RUNTIME
fun literal() {
    var b : Boolean
    b = true
    println(<weak_warning descr="Condition is always true">b</weak_warning>)
}
fun returnLiteral() : Boolean = false
fun booleanBoxed(x:Boolean?) {
    if (x != true) { }
}
fun noWarningOnConstant(x : Boolean) {
    val b = true
    if (x || b) {}
}