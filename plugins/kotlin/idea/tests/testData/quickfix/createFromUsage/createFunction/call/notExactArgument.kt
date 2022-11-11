// "Create function 'synchronized'" "false"
// ACTION: Convert expression to 'Int'
// ACTION: Converts the assignment statement to an expression
// ACTION: Round using roundToInt()
// ERROR: Type mismatch: inferred type is Float but Int was expected
// WITH_STDLIB

fun test() {
    var value = 0
    synchronized(value) {
        value = <caret>10 / 1f
    }
}