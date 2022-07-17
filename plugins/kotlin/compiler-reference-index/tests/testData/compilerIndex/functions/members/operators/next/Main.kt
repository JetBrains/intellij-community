class Main {
    operator fun iterator() = Main()
    operator fun hasNext() = false
    operator fun ne<caret>xt() = Main()
}
