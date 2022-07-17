// WITH_STDLIB

data class My(val first: String, val second: Int)

fun foo(list: List<My>) {
    list.forEach { my<caret> ->
        println(my.second)
    }
}