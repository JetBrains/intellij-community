fun test(x: Any): Any? {
    val y = foo(<caret>x as String ?: return null)
    return y
}

fun foo(x: String?): Any? {
    return x
}