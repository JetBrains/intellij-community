// FIX: Convert to collection builder

fun test(a: List<Int>, b: List<Int>, c: List<Int>): Int = 1 + (a +<caret> b + c).size