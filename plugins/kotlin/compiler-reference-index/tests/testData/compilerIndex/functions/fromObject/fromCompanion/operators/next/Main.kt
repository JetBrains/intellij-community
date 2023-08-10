class Main {
    companion object {
        operator fun Int.iterator() = 42
        operator fun Int.hasNext() = false
        operator fun Int.ne<caret>xt() = 3
    }
}
