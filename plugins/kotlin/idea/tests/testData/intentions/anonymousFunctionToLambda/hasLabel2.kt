inline fun a(block: () -> String) = block()

val test = <caret>fun(i: Int, s: String): String {
    if (i == 42) return s
    a block@{
        a block1@{
            return "42"
        }
    }
    return if (s == "test") "$s" else "$i"
}