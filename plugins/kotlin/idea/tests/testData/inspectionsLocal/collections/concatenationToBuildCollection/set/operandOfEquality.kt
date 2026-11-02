// FIX: Convert to collection builder

fun test(a: Set<Int>, b: Set<Int>, c: Set<Int>, expected: Set<Int>): Boolean = a +<caret> b + c == expected