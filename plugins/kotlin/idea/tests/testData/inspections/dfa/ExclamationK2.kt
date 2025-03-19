// WITH_STDLIB
@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun notNull(i:Int) {
    i!!.toLong()
}
@Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
fun range(i : Int?) {
    if (i!! > 5) {
        if (<warning descr="Condition 'i!! < 3' is always false">i!! < 3</warning>) {

        }
        if (<warning descr="Condition 'i < 3' is always false">i < 3</warning>) {

        }
    }
}
fun alwaysNull(i : Int?) {
    if (i == null) {
        // K2 difference: 1. no ALWAYS_NULL warning. 2. No "Value of 'i' is always null" on lho, due to type system change. 
        // ALWAYS_NULL should be reported by a separate inspection, which is enough.
        i<warning descr="Operation will always fail as operand is always null">!!</warning>
    }
    null!! // too obvious, looks intended
}
