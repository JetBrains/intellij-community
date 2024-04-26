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
        <warning descr="[ALWAYS_NULL] The result of the expression is always null"><weak_warning descr="Value of 'i' is always null">i</weak_warning></warning><warning descr="Operation will always fail as operand is always null">!!</warning>
    }
    null!! // too obvious, looks intended
}
