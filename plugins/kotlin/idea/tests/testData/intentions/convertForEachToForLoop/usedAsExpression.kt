// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(list: List<Int>) = list.forEach<caret> { println(it) }