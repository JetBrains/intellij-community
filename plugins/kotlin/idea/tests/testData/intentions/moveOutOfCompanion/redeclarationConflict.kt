// SHOULD_FAIL_WITH: Class 'Test' already contains function f1(Int)
// AFTER-WARNING: Parameter 'n' is never used
// AFTER-WARNING: Parameter 'n' is never used
class Test {
    fun f1(n: Int){}
    companion object{
        fun <caret>f1(n: Int){}
    }
}