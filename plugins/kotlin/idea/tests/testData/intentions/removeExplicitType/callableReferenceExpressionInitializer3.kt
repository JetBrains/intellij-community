// AFTER-WARNING: Parameter 't' is never used
fun <T> T.foo(t: T) {}

fun test(): <caret>Function2<String, String, Unit> = String::foo