// AFTER-WARNING: Variable 'predicate' is never used
fun test(i: Int) {
    val predicate: () -> Boolean =
        if (i == 1) {
            { true }
        } else {
            <caret>{ -> false }
        }
}