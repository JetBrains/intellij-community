// WITH_STDLIB
fun main(args: Array<String>) {
    <caret>if (args.isNotEmpty() || foo()) {}
}

fun foo(): Nothing = throw RuntimeException()