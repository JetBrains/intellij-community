// WITH_STDLIB
fun lenthTest(x : Int) {
    val data = "Value = ${x}"
    if (<warning descr="Condition 'data.length > 5' is always true">data.length > 5</warning>) { }
}
fun charTemplate(c: Char, n: Char?) {
    val a = 'a'
    val s1 = "$a"
    if (<warning descr="Condition 's1 == \"a\"' is always true">s1 == "a"</warning>) {}
    val s2 = "b$a"
    if (<warning descr="Condition 's2 == \"ba\"' is always true">s2 == "ba"</warning>) {}
    val s3 = "b" + a
    if (<warning descr="Condition 's3 == \"ba\"' is always true">s3 == "ba"</warning>) {}
    val s4 = "x$c"
    if (<warning descr="Condition 's4.length == 2' is always true">s4.length == 2</warning>) {}
    val s5 = "x$n"
    if (s5.length == 2) {}
}
fun nonString(x : X) {
    if ("$x" == "hello") {}
}
class X {
    override fun toString(): String = "hello"
}