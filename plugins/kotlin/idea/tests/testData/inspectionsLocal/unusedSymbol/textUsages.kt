class ABC {
    internal fun f<caret>oo() {}
    // use foo sometimes
}

fun main() {
    ABC()
}
