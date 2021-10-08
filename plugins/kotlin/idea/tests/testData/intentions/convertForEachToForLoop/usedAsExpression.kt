// IS_APPLICABLE: false
// WITH_RUNTIME
fun foo(list: List<Int>) = list.forEach<caret> { println(it) }