data class A(val x: String)
const val STR = ""
fun main(a: A) {
    <caret>if (a.x == STR) {

    }
}