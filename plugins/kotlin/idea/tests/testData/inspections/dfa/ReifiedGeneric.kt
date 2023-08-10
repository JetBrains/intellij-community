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

// Non-reified
class TreeWalker<T> {
    // KTIJ-23521
    fun test() : Boolean {
        var current: TreeWalker<*>? = this
        while (<warning descr="Condition 'current != null' is always true">current != null</warning>) {
            if (current.type is <error descr="[CANNOT_CHECK_FOR_ERASED] Cannot check for instance of erased type: T">T</error>)
            return true
            current = current.parent()
        }
        return false
    }

    var type: Any = Any()

    fun parent() = this
}