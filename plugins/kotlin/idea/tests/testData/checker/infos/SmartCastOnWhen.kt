// FIR_IDENTICAL


fun baz(s: String?): Int {
    if (s == null) return 0
    return when(<info descr="Smart cast to kotlin.String" tooltip="Smart cast to kotlin.String">s</info>) {
        "abc" -> <info>s</info>
        else -> "xyz"
    }.length
}
