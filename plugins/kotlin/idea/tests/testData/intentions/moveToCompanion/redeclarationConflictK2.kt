// SHOULD_FAIL_WITH: Following declarations would clash: to move function 'fun f1(n: Int)' and destination function 'fun f1(n: Int)' declared in scope companion object Test9.Companion
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: Parameter 'n' is never used
// IGNORE_K1
class Test9{
    fun <caret>f1(n: Int){}
    companion object{
        fun f1(n: Int){}
    }
}