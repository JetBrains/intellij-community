// WITH_RUNTIME
class T<U>

operator fun <U> T<U>.iterator(): Iterator<U> = listOf<U>().iterator()

fun test() {
    T<Int>()<caret>
}