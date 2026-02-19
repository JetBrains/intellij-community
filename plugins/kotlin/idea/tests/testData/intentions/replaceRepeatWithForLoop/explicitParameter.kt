// WITH_STDLIB
fun foo() {
    val it = "outer"
    val i = "i"
    <caret>repeat(5) { it ->
        println(it)
        println(i)
        println(it.toString())
    }
}