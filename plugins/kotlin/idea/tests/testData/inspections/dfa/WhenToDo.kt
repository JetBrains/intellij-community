// WITH_STDLIB
enum class Enum {
    A, B, C
}

@OptIn(ExperimentalStdlibApi::class)
fun main() {
    val value = Enum.entries.random()
    if(value != Enum.C) {
        when (value ) {
            Enum.A -> TODO()
            Enum.B -> TODO()
            Enum.C -> TODO()
        }
    }
}