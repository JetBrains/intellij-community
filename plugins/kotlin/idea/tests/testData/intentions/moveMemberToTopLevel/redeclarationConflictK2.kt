// SHOULD_FAIL_WITH: Following declarations would clash: to move function 'fun f1(n: Int)' and destination function 'fun f1(n: Int)' declared in scope default
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: Parameter 'n' is never used
// IGNORE_K1
object Test {
    fun <caret>f1(n: Int) {}
}

fun f1(n: Int) {}
