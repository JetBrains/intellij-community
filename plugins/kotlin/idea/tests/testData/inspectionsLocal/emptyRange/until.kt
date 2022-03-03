// PROBLEM: This range is empty. Did you mean to use 'downTo'?
// WITH_STDLIB
fun test() {
    <caret>0 until -1
}
