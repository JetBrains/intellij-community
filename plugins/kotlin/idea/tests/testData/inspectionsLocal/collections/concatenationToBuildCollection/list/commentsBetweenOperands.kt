// FIX: Convert to collection builder

fun test(a: List<Int>, b: List<Int>, c: List<Int>): List<Int> =
    <caret>a + /* from cache */ b + c // tail