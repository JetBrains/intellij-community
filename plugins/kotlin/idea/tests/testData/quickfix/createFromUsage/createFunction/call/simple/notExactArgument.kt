// "Create function 'synchronized'" "false"
// ERROR: Type mismatch: inferred type is Float but Int was expected
// WITH_STDLIB
// K2_AFTER_ERROR: Assignment type mismatch: actual type is 'Float', but 'Int' was expected.
// K2_AFTER_ERROR: Synchronizing on 'Int' is forbidden.

fun test() {
    var value = 0
    synchronized(value) {
        value = <caret>10 / 1f
    }
}