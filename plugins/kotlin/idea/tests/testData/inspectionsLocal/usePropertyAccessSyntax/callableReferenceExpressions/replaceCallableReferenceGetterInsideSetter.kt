// FIX: Use property access syntax
// LANGUAGE_VERSION: 2.1

fun main() {
    val j = J()
    j.<caret>setX(j::<caret>getX)
}
