data class My(val a: String, val second: Int, val third: Boolean)

fun foo(list: List<My>) {
    list<caret>
}
