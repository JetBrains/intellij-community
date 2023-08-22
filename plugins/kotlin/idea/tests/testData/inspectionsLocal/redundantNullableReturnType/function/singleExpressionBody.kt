// PROBLEM: 'foo' always returns non-null type
// WITH_STDLIB
fun foo(xs: List<Int>): Int?<caret> = xs.first()