// IGNORE_FIR

data class Box(val v: Int)
fun consume(x: Int) {}

fun some() {
    val (s) = Box(0)
    var (x) = Box(1)

    consume(s)
    consume(x)

    x = x * 2 + 2
    consume(x)
}
