// WITH_STDLIB
fun test(x : Int) {
    if (x > 5) {
        fail(x)
    }
    if (<warning descr="Condition 'x == 10' is always false">x == 10</warning>) {}
}
fun fail(value: Int) : Nothing {
    throw RuntimeException("Oops: ${value}")
}