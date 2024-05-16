// WITH_STDLIB
fun process(deque: ArrayDeque<Data>) {
    var curLevel = 1
    var curSum: Int = 0

    while (deque.isNotEmpty()) {
        with(deque.removeFirst()) {
            if (curLevel == level) {
                curSum += payload
            } else {
                if (curSum > 10) {
                    throw AssertionError()
                }
            }
        }
    }
}

data class Data(val payload: Int, val level: Int)

fun main() {
    process(ArrayDeque(listOf(Data(100, 1), Data(100, 2))))
}