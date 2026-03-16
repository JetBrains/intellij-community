// WITH_STDLIB
fun getFlag(): Boolean? = null

fun test() {
    val flag = getFlag()
    i<caret>f (flag == true) {
        println("true")
    } else if (flag == false) {
        println("false")
    } else {
        println("null")
    }
}
