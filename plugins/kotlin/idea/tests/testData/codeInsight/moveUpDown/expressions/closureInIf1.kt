// MOVE: down
fun foo(i: Int) {
    <caret>run {
    }
    if (i in run { 1..2 }) {
    }
}