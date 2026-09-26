// SHOULD_FAIL_WITH: Following declarations would clash: to move function 'fun f1(n: Int)' and destination function 'fun f1(n: Int)' declared in scope class Test
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: Parameter 'n' is never used

class Test {
    fun f1(n: Int){}
    companion object{
        fun <caret>f1(n: Int){}
    }
}