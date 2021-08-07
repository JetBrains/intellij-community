// WITH_RUNTIME

fun List<Int>.transform(x: Int = 0, f: (Int) -> Int) = map { f(it + x) }

fun bar(x: Int) = x * x

val y = listOf(1, 2, 3).transform { <caret>bar(it) }
