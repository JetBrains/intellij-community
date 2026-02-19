// FIX: Move unary operator to previous line
fun test() {
  val c: Long = 5L
  val a = c
    <caret>+ 5 - 2 + 1
}