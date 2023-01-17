// WITH_STDLIB
fun main() {
    val a: Any = 0
    println(<warning descr="Condition 'a is Int' is always true">a is Int</warning>)
}

fun main2(b: Boolean) {
    var a: Any = 0
    if (b) a = 1
    println(<warning descr="Condition 'a is Int' is always true">a is Int</warning>)
}