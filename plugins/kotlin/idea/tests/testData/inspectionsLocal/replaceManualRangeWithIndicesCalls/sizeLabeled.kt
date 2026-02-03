// WITH_STDLIB
fun main() {
    println(listOf(1, 2, 3).doubled())
}

fun <T> List<T>.doubled(): List<T> {
    val result = mutableListOf<T>()
    for (i in 0..<this@doubled.s<caret>ize) {
        result.add(this@doubled[i])
        result.add(this@doubled[i])
    }
    return result
}