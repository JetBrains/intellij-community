// PROBLEM: Condition is always true
// FIX: none
// WITH_RUNTIME
fun test(x : List<String>) {
    val y = 10
    x.forEach { a ->
        if (a.isEmpty()) return@forEach
    }
    if (<caret>y == 10) {}
}