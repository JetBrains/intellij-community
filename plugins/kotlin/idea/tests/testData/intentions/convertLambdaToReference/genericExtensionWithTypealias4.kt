// WITH_STDLIB
typealias PairIntT<T> = Pair<Int, T>

fun <U> PairIntT<U>.boo() = this

fun main() {
    Pair(1, 1).apply {<caret> this.boo() }
}
