// PROBLEM: Replace negated 'isBlank' with 'isNotBlank'
// FIX: Replace negated 'isBlank' with 'isNotBlank'
// WITH_STDLIB
fun test(s: String) {
    val b = !s.isBlank<caret>()
}