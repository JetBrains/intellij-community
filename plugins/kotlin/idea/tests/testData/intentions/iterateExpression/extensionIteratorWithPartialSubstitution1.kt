// WITH_STDLIB
class T<U, V>

operator fun <X> T<X, String>.iterator(): Iterator<X> = listOf<X>().iterator()

fun test() {
    T<Int, String>()<caret>
}