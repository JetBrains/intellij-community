// AFTER-WARNING: Parameter 'x' is never used
fun foo(x: Int) {
    class Local<caret>(val y: Int) {
        val z = x
    }
}