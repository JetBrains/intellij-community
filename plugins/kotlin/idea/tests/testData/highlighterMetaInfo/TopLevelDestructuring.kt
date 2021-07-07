// IGNORE_FIR

val (a, b) by lazy { Pair(1, 2) }
val (c, d) = run { Pair(3, 4) }


class Foo {
    val (a, b) by lazy { Pair(1, 2) }
    val (c, d) = run { Pair(3, 4) }
}
