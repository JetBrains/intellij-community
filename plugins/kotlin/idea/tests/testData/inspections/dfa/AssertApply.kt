// WITH_STDLIB
// KTIJ-29651
fun main() {
    var a = 0
    println(a)
    assert(<warning descr="Condition 'false.apply { a = 1; }' is always false">false.apply { a = 1; }</warning>)
    println(a)
    println(<warning descr="Condition 'a == 0' is always false">a == 0</warning>)
}

