// MOVE: down
fun foo(i: Int) {
    <caret>run {
    }
    while (i in run { 1..2 }) {
        println(i)
    }
}