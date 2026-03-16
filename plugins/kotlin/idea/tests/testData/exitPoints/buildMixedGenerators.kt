// WITH_COROUTINES
import kotlinx.coroutines.flow.*

fun f() {
    val seq = <info descr="null">~sequence</info> {
        <info descr="null">yield</info>(1)

        val list = buildList {
            add(10)

            val set = buildSet {
                add(100)

                val map = buildMap {
                    put("key", 1000)
                    putAll(mapOf("a" to 1))
                }
            }

            addAll(listOf(20, 30))
        }

        val str = buildString {
            append("hello")
            appendLine("world")
        }

        val fl = flow {
            emit(999)
            emitAll(flowOf(1, 2))
        }

        <info descr="null">yield</info>(2)
        <info descr="null">yieldAll</info>(list)
    }
}