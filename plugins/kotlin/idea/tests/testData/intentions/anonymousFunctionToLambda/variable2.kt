val test = <caret>fun(i: Int, s: String): String {
    if (i == 42) return s

    return if (s == "test") "$s" else "$i"
}

inline fun a(block: () -> Unit) = block()