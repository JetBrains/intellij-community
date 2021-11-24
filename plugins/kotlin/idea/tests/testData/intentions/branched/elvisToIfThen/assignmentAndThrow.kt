// WITH_STDLIB
fun foo(s: String?) {
    val t = s?.hashCode() ?:<caret> throw Exception()
}
