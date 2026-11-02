// FIX: Convert to collection builder

fun test(a: Set<Int>, b: Set<Int>, c: Set<Int>): Set<Int> {
    return a<caret> + // first
            b /* before operator */ - c +
            listOf(/* inside */ 1) + // last
            2
}