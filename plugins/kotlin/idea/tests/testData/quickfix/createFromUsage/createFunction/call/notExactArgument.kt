// "Create function 'synchronized'" "false"
// ERROR: Type mismatch: inferred type is Float but Int was expected
// WITH_STDLIB

fun test() {
    var value = 0
    synchronized(value) {
        value = <caret>10 / 1f
    }
}