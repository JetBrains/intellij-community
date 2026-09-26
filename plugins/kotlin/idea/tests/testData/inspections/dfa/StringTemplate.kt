// WITH_STDLIB
fun lenthTest(x : Int) {
    val data = "Value = ${x}"
    if (<warning descr="Condition 'data.length > 5' is always true">data.length > 5</warning>) { }
}
fun nonString(x : X) {
    if ("$x" == "hello") {}
}
class X {
    override fun toString(): String = "hello"
}