// "Replace with 'this.minByOrNull(selector)'" "true"
// WITH_STDLIB
// LANGUAGE_VERSION: 1.5
class C<T> {
    fun test() {
        listOf(1).<caret>minBy { it }
    }
}