// PROBLEM: Replace negated 'isEmpty' with 'isNotEmpty'
// FIX: Replace negated 'isEmpty' with 'isNotEmpty'
// WITH_STDLIB
fun test(s: String) {
    val b = !s.isEmpty<caret>()
}