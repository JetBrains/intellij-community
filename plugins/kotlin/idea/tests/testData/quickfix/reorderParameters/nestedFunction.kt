// "Reorder parameters" "true"
fun foo(b: Int) {
    fun bar(
        a: Int = b<caret>,
        b: Int = 2
    ) = Unit
}
