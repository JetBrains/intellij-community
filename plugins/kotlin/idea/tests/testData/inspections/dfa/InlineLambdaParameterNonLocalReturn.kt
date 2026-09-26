// WITH_STDLIB

fun main() {
    a {
        return
    }
}

inline fun a(block: () -> Unit) {
    var returnedOrThrew = false
    println("Let's go")
    try {
        return block().also {
            returnedOrThrew = true
        }
    } catch (t: Throwable) {
        returnedOrThrew = true
        throw t
    } finally {
        if (!returnedOrThrew) {
            println("actually…")
        }
    }
}
