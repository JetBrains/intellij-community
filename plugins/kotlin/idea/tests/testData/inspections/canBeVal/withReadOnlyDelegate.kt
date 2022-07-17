// WITH_STDLIB

fun foo() {
    var s: String by lazy { "Hello!" }
    s.hashCode()
}
