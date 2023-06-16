// FIX: Remove unused unary operator
fun test(foo: Int?): Int {
    val a = 1 + 2
    - 3<caret>
    return a
}