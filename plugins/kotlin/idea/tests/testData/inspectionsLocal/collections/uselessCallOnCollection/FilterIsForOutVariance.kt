// WITH_STDLIB

inline fun <reified T> test(x: Array<out T>) {
    val y: List<T> = x.<caret>filterIsInstance<T>()
}
