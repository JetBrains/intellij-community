package sample

class SmartList<T> {
    constructor (x: T) {}
    constructor (x: Collection<T>) {}
}

fun append(x: Any) {}
fun append(xs: Collection<*>) {}
fun append2(y: String, x: Any, z: Int) {}
fun append2(y: String, xs: Collection<*>, z: Int) {}

fun <T> append3(t: T) {}
fun <T> append3(xs: Collection<T>) {}

fun invoke() {
    SmartList(1..10)
    append('a'..'c')
    append(1..10)
    append(1L..10L)
    append(1U..5U)
    append(1UL..5UL)
    append(1.rangeTo(10))
    append2("", 1..10, 0)
    append3(1.rangeTo(10))
}
