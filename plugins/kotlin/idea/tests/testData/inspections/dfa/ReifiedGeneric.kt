// WITH_STDLIB
inline fun <reified T : CharSequence> CharSequence.test(): T {
    val result = repl(this)
    return result as? T ?: repl(result) as T
}

fun repl(x: CharSequence?): CharSequence? = x

inline fun <reified T: Number> number(t: T) {
    var x: Any = t
    if (<warning descr="Condition 'x is String' is always false">x is String</warning>) {}
    x = 123
    // Cannot say anything
    if (x is T) {}
    x = "hello"
    // TODO: support 'always false' here
    if (x is T) {}
}