// AFTER-WARNING: Parameter 'fn' is never used
// AFTER-WARNING: Parameter 'p' is never used
// AFTER-WARNING: Parameter 'p' is never used
// AFTER-WARNING: Parameter 'q' is never used
// AFTER-WARNING: Parameter 'q' is never used
fun overloadFun(p: Int, q: Long) {}
fun overloadFun(p: String, q: Long) {}

fun <T, U> foo(fn: (T, U) -> Unit) {}

fun test() {
    foo({<caret> x: String, y: Long -> overloadFun(x, y) })
}