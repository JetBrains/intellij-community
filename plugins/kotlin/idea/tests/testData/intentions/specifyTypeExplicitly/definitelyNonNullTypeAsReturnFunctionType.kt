// AFTER-WARNING: Unchecked cast: String to T & Any

fun <T> bar(): T & Any = "" as (T & Any)
fun <T> <caret>foo() = bar<T>()