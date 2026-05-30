data class My(val a: String, val second: Int, val third: Boolean)

fun foo(list: List<My>) {
    for ((<selection>a<caret></selection>, second, third) in list) {

    }
}
