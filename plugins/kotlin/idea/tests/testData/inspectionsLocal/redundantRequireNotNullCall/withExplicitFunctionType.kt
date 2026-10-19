// WITH_STDLIB
fun main() {
    val length = requireNotN<caret>ull<(String) -> Int>({ it.length })
    println(length("abc"))
}