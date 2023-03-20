// IS_APPLICABLE: false
// ERROR: Unresolved reference: s
fun foo() {
    val x = bar<<caret>Any>(s) // s not definded, can't be inferred
}

fun <T> bar(t: T): Int = 1