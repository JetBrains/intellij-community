// AFTER-WARNING: Unchecked cast: String to T & Any

fun <T> bar(): T & Any = "" as (T & Any)

class My<T> {
    fun <caret>foo() = bar<T>()
}