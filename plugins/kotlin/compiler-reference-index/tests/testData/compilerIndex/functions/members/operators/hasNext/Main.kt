class Main {
    operator fun iterator() = Main()
    operator fun has<caret>Next() = false
    operator fun next() = Main()
}
