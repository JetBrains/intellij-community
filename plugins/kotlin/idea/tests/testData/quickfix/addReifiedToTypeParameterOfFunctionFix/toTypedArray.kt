// "Make 'T' reified and 'flatten' inline" "true"
// WITH_STDLIB

fun <T> Array<Array<T>>.flatten(): Array<T> {
    return this.flatMap { it.asIterable() }.toTypedArray<caret>()
}