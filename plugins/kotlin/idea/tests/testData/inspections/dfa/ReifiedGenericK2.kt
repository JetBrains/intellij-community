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
    if (<warning descr="[USELESS_IS_CHECK] Check for instance is always 'false'.">x is T</warning>) {}
}

// Non-reified
class TreeWalker<T> {
    // KTIJ-23521
    fun test() : Boolean {
        var current: TreeWalker<*>? = this
        // Difference from K1: always true warning is not reported
        // K1 does not report smart-cast on current.type and current.parent() below for some reason.
        // K2 reports it (which looks correct), and this causes the warning suppression
        while (current != null) {
            // Difference from K1: error messages is spelled differently
            if (current.type is <error descr="[CANNOT_CHECK_FOR_ERASED] Cannot check for instance of erased type 'T (of class TreeWalker<T>)'.">T</error>)
            return true
            current = current.parent()
        }
        return false
    }

    var type: Any = Any()

    fun parent() = this
}