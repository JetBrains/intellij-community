// WITH_STDLIB
fun <T> T.check() {
    val b = this is String
    if (<warning descr="Condition 'b && this is Int' is always false">b && <warning descr="Condition 'this is Int' is always false when reached">this is Int</warning></warning>) {
        println()
    }
}