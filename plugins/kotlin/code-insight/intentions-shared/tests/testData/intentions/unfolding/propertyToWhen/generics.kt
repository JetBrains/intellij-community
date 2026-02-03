data class C<T>(val value: T)

fun test(n: Int): C<String>? {
    val res<caret> = when (n) {
        1 -> C("one")
        2 -> C("two")
        else -> null
    }

    return res
}