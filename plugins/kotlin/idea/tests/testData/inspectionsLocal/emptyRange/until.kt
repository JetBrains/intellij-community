// PROBLEM: This range is empty. Did you mean to use 'downTo'?
// WITH_RUNTIME
fun test() {
    <caret>0 until -1
}
