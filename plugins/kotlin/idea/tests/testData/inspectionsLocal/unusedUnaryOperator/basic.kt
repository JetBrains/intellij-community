// FIX: Remove unused unary operator
fun test(foo: Int?): Int {
    val a = 1 + 2
    <caret>+ 3
    return a
}