fun <U, V> Map<U, V>.foo() {}

fun test(): <caret>Function1<Map<String, Int>, Unit> = Map<String, Int>::foo