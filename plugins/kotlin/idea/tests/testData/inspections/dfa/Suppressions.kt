// WITH_STDLIB
fun assertCall(x: Int, b: Boolean, c: Boolean) {
    if (x < 0) return
    if (Math.random() > 0.5) {
        assert(x >= 0)
    }
    if (Math.random() > 0.5) {
        assert(b && x >= 0)
    }
    if (Math.random() > 0.5) {
        assert(b || x >= 0)
    }
    if (Math.random() > 0.5) {
        assert(<warning descr="Condition 'c && !(b || x >= 0)' is always false">c && <warning descr="Condition '!(b || x >= 0)' is always false when reached">!(b || <warning descr="Condition 'x >= 0' is always true when reached">x >= 0</warning>)</warning></warning>)
    }
    if (Math.random() > 0.5) {
        assert(c && !(b || x < 0))
    }
    if (Math.random() > 0.5) {
        assert(<warning descr="Condition 'x < 0' is always false">x < 0</warning>)
    }
}
fun requireCall(x: Int) {
    if (x < 0) return
    require(x >= 0)
    require(<warning descr="Condition 'x < 0' is always false">x < 0</warning>)
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

fun updateChain(b: Boolean, c: Boolean): Int {
    var x = 0
    if (b) x = x or 1
    if (c) x = x or 2
    return x
}
fun updateChainBoolean(b: Boolean, c: Boolean): Boolean {
    var x = false
    x = x || b
    x = x || c
    return x
}
fun updateChainInterrupted(b: Boolean, c: Boolean): Int {
    var x = 0
    x++
    <warning descr="Value of 'x--' is always zero">x--</warning>
    if (b) x = <weak_warning descr="Value of 'x' is always zero">x</weak_warning> or 1
    if (c) x = x or 2
    return x
}
