// WITH_RUNTIME
fun assertCall(x: Int) {
    if (x < 0) return
    assert(x >= 0)
    assert(<warning descr="Condition is always false">x < 0</warning>)
}
fun requireCall(x: Int) {
    if (x < 0) return
    require(x >= 0)
    require(<warning descr="Condition is always false">x < 0</warning>)
}
fun compilerWarningSuppression() {
    val x: Int = 1
    @Suppress("SENSELESS_COMPARISON")
    if (x == null) {}
}
fun compilerWarningDuplicate(x : Int) {
    // Reported as a compiler warning: suppress
    if (<warning descr="[SENSELESS_COMPARISON] Condition 'x != null' is always 'true'">x != null</warning>) {
    }
}
fun compilerWarningDuplicateWhen(x : X) {
    // Reported as a compiler warning: suppress
    when (x) {
        <warning descr="[USELESS_IS_CHECK] Check for instance is always 'true'">is X</warning> -> {}
    }
}
fun nothingOrNull(s: String?): String? {
    return s?.let {
        if (it.isEmpty()) return null
        return s
    }
}
fun nothingOrNullToElvis(s: String?): Boolean {
    return s?.let {
        if (it.isEmpty()) return false
        return s.hashCode() < 0
    } ?: false
}
// f.get() always returns null but it's inevitable: we cannot return anything else, hence suppress the warning
fun alwaysNull(f : MyFuture<Void>) = f.get()
fun unusedResult(x: Int) {
    // Whole condition is always true but reporting it is not very useful
    x > 0 || return
}

interface MyFuture<T> {
    fun get():T?
}
class X