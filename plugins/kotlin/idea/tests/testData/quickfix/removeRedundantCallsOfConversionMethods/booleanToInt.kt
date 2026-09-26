// "Remove redundant calls of the conversion method" "false"
fun Boolean.toInt() = if (this) 1 else 0

fun test(x: Int, y: Int): Int {
    return (x > y).toInt()<caret>
}