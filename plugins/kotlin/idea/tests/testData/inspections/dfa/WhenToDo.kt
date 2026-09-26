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

@OptIn(ExperimentalStdlibApi::class)
fun main2() {
    val value = Enum.entries.random()
    if(value != Enum.C) {
        when (value ) {
            Enum.A -> {}
            Enum.B -> {}
            // Report, because the unreachable case doesn't throw,
            // so it's unclear whether it's intended or mistaken.
            <warning descr="'when' branch is never reachable">Enum.C</warning> -> {}
        }
    }
}