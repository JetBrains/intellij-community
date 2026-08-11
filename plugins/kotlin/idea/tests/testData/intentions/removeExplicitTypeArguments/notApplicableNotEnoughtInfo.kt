// PROBLEM: none
// IS_APPLICABLE: false
// ERROR: Unresolved reference: LinkedList
// K2_ERROR: UNRESOLVED_REFERENCE
fun foo() {
    val x = bar<caret><String>()
}

fun <T> bar() : List<T> = LinkedList<T>();