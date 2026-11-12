// WITH_STDLIB
operator fun Char.times(other: Char): Char = this
operator fun Int.times(other: Char): Char = other
operator fun Int.plus(other: Char): Int = this + other.code
operator fun Char.unaryMinus(): Char = this

fun customBinary() {
    if ('a' * 'b' == 'a') {}
    if ('a' * 'b' == 'b') {}
    if (0 * 'a' == 'a') {}
    if (0 * 'a' == 'b') {}
}

fun customUnary() {
    if (-'a' == 'a') {}
}

fun customCompound() {
    var x = 0
    x += 'a'
    if (x == 0) {}
}

fun builtIn(c: Char) {
    if (<warning descr="Condition ''a' + 1 == 'b'' is always true">'a' + 1 == 'b'</warning>) {}
    if (<warning descr="Condition ''b' - 'a' == 1' is always true">'b' - 'a' == 1</warning>) {}
    if (<warning descr="Condition '2 * 3 == 6' is always true">2 * 3 == 6</warning>) {}
    var y = 1
    y += 2
    if (<warning descr="Condition 'y == 3' is always true">y == 3</warning>) {}
    val z = 5
    if (<warning descr="Condition '-z == -5' is always true">-z == -5</warning>) {}
}
