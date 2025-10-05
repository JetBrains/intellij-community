// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
// ALLOW_ERRORS
interface ArcadeGame<T1> {
    fun load(x: T1 & Any): T1 & Any
}

fun <T> (T & Any).bar() {}
fun (String & Any).foo() {}