// WITH_STDLIB
fun notNull(i:Int) {
    i<warning descr="[UNNECESSARY_NOT_NULL_ASSERTION] Unnecessary non-null assertion (!!) on a non-null receiver of type Int">!!</warning>.toLong()
}
fun range(i : Int?) {
    if (i!! > 5) {
        if (<warning descr="Condition 'i!! < 3' is always false">i<warning descr="[UNNECESSARY_NOT_NULL_ASSERTION] Unnecessary non-null assertion (!!) on a non-null receiver of type Int">!!</warning> < 3</warning>) {

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
