// AFTER-WARNING: Parameter 'a' is never used
inline fun block(block: () -> String) = block()

val test = <caret>fun(i: Int, s: String): String {
    if (i == 42) return s
    println(block {
        block block1@{
            return "42"
        }

        "44"
    })
    return if (s == "test") "$s" else "$i"
}

fun println(a: Any) {}