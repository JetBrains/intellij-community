// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
/**
 * @param x foo and [baz]
 * @param y bar
 * @return notALink here
 */
fun f(x: Int, y: Int): Int {
return x + y
}

fun baz() {}