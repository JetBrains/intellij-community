// PROBLEM: none
// WITH_STDLIB

inline fun <reified T> test(x: Array<in T>) {
    val y: List<T> = x.<caret>filterIsInstance<T>()
}